package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.annotation.Ignore;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.pojo.FileUploadResponse;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import com.chua.starter.filesystem.support.utils.FileNaming;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件对象接口（单次上传与回显）。
 *
 * <p>落盘与读取全部走 {@link FileStorageTemplate} 的 {@code FileStorage} SPI，对象键为
 * {@code 归一化桶名/随机文件名}，文件名由服务端生成，不接受调用方命名，从根源上规避路径穿越
 * 与同名覆盖。</p>
 *
 * <p>回显以 {@link Ignore} 开放匿名访问，供 img 标签直链；按流写出，不把整个对象读进堆。上传需登录态。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/file")
@Tag(name = "文件对象接口")
public class FileObjectController {

    /**
     * 回显路径前缀，与 view 映射保持一致
     */
    private static final String VIEW_PATH = "/v2/file/view/";

    /**
     * 本地存储类型别名（历史入参，兼容 ossType=LOCAL/FILESYSTEM）
     */
    private static final List<String> LOCAL_ALIASES = List.of("local", "filesystem");

    /**
     * 流转发缓冲区字节数
     */
    private static final int COPY_BUFFER_BYTES = 8192;

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileObjectController.class);

    /**
     * 文件存储模板
     */
    private final FileStorageTemplate fileStorageTemplate;

    /**
     * 上传配置（大小上限、扩展名白名单、回显前缀）
     */
    private final FileStorageUploadProperties uploadProperties;

    /**
     * 单次上传文件到配置的文件存储。
     *
     * @param file       文件内容
     * @param fileBucket 存储桶，为空时使用 default
     * @param ossType    存储提供者名称，为空或本地别名时使用默认提供者
     * @param request    HTTP 请求对象，用于拼接回显地址
     * @return 上传结果，含回显地址与对象键
     */
    @Operation(summary = "上传文件")
    @RequestMapping(value = "/upload", method = {RequestMethod.PUT, RequestMethod.POST})
    public ReturnResult<FileUploadResponse> upload(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "存储桶") @RequestParam(value = "fileBucket", required = false) String fileBucket,
            @Parameter(description = "存储提供者") @RequestParam(value = "ossType", required = false) String ossType,
            HttpServletRequest request
    ) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.illegal("文件不能为空");
        }
        if (file.getSize() > maxBytes()) {
            return ReturnResult.illegal("文件大小超过上限 " + (maxBytes() / 1024 / 1024) + "MB");
        }
        String provider = resolveProvider(ossType);
        FileStorage storage = provider == null ? null : fileStorageTemplate.getStorage(provider);
        if (storage == null) {
            return ReturnResult.illegal(StringUtils.hasText(ossType)
                    ? "未配置的存储提供者: " + ossType : "未配置文件存储提供者");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        if (extension == null) {
            return ReturnResult.illegal("不支持的文件类型");
        }

        String bucket = FileNaming.normalizeBucket(fileBucket);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String key = bucket + "/" + fileName;
        try (InputStream in = file.getInputStream()) {
            PutObjectResult put = storage.putObject(PutObjectRequest.builder()
                    .filePath(bucket)
                    .fileName(fileName)
                    .inputStream(in)
                    .build());
            if (put == null || ObjectResult.ResultCode.FAILURE == put.getResultCode()) {
                MODULE_LOG.error("文件存储写入失败: provider={} key={}", provider, key);
                return ReturnResult.error("文件保存失败");
            }
            String storedKey = put.getKey() == null ? key : put.getKey();
            String accessUrl = accessUrl(request, provider, storedKey);
            return ReturnResult.ok(new FileUploadResponse(
                    accessUrl,
                    storedKey,
                    bucket,
                    provider,
                    fileName,
                    file.getSize()));
        } catch (IOException e) {
            MODULE_LOG.error("文件上传失败: provider={} key={}", provider, key, e);
            return ReturnResult.error("文件保存失败");
        }
    }

    /**
     * 回显已上传的文件，供 img 标签匿名加载。
     *
     * <p>路径为 {@code /v2/file/view/{提供者}/{对象键}}。首段不是已注册提供者时，按历史地址
     * （{@code /v2/file/view/{对象键}}）处理：整段作对象键走默认提供者。</p>
     *
     * @param download 为真时以附件下载语义响应
     * @param request  HTTP 请求对象，用于解析提供者与对象键
     * @param response HTTP 响应对象
     */
    @Operation(summary = "查看文件")
    @Ignore
    @GetMapping("/view/**")
    public void view(
            @Parameter(description = "是否作为附件下载") @RequestParam(value = "download", required = false,
                    defaultValue = "false") boolean download,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String rest = parseViewPath(request);
        if (rest == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }
        int separator = rest.indexOf('/');
        FileStorage storage = fileStorageTemplate.getStorage(rest.substring(0, separator));
        String key = storage == null ? rest : rest.substring(separator + 1);
        if (storage == null) {
            storage = fileStorageTemplate.getDefaultStorage();
        }
        if (storage == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }
        GetObjectResult result = storage.getObject(key);
        if (result == null || ObjectResult.ResultCode.FAILURE == result.getResultCode()
                || result.getInputStream() == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }
        try (InputStream in = result.getInputStream()) {
            writeObject(in, key, response, download ? attachmentDisposition(key) : null);
        }
        response.flushBuffer();
    }

    /**
     * 把存储对象按流写回响应。
     *
     * @param in          对象输入流
     * @param key         对象键，用于推断内容类型与兜底文件名
     * @param response    HTTP 响应对象
     * @param disposition 附件响应头，为空表示内联展示
     */
    private void writeObject(InputStream in, String key, HttpServletResponse response, String disposition)
            throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        // 先探一个字节：目录占位对象等零字节结果按不存在处理，且不提交响应头
        int first = in.read();
        if (first == -1) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(resolveMediaType(key).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (disposition != null) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
        }
        try (OutputStream out = response.getOutputStream()) {
            out.write(first);
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * 解析回显路径中 {@code /v2/file/view/} 之后的剩余路径。
     *
     * @param request HTTP 请求对象
     * @return 已解码的剩余路径，路径不完整或含穿越段时返回 空
     */
    private String parseViewPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(VIEW_PATH)) {
            return null;
        }
        String rest = UriUtils.decode(path.substring(VIEW_PATH.length()), StandardCharsets.UTF_8);
        if (rest.indexOf('/') < 0) {
            return null;
        }
        for (String segment : rest.split("/")) {
            if ("..".equals(segment) || ".".equals(segment) || segment.isEmpty()) {
                return null;
            }
        }
        return rest;
    }

    /**
     * 拼接回显地址，优先使用配置的 {@code view-prefix}，未配置时退回当前应用的上下文路径。
     *
     * @param request  HTTP 请求对象
     * @param provider 存储提供者名称
     * @param key      对象键
     * @return 回显地址
     */
    private String accessUrl(HttpServletRequest request, String provider, String key) {
        String prefix = uploadProperties.getViewPrefix();
        if (!StringUtils.hasText(prefix)) {
            prefix = request.getContextPath() + VIEW_PATH;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + provider + "/" + key;
    }

    /**
     * 构造附件下载响应头，文件名按 RFC 5987 编码以兼容非 ASCII 名称。
     *
     * @param key 对象键
     * @return Content-Disposition 值
     */
    private String attachmentDisposition(String key) {
        String name = StringUtils.getFilename(key);
        if (name == null) {
            name = "download";
        }
        String fallback = name.replaceAll("[\\r\\n\"]", "");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 定位存储提供者名称，本地别名与空值收敛到默认提供者。
     *
     * @param ossType 存储提供者名称（可空）
     * @return 提供者名称，无法解析时返回 空
     */
    private String resolveProvider(String ossType) {
        if (StringUtils.hasText(ossType) && fileStorageTemplate.getStorage(ossType) != null) {
            return ossType;
        }
        if (!StringUtils.hasText(ossType) || isLocalAlias(ossType)) {
            return fileStorageTemplate.getDefaultStorageName();
        }
        return null;
    }

    /**
     * 是否本地存储类型别名。
     *
     * @param ossType 存储类型
     * @return 是否本地别名
     */
    private boolean isLocalAlias(String ossType) {
        String lowered = ossType.toLowerCase(Locale.ROOT);
        return LOCAL_ALIASES.stream().anyMatch(lowered::contains);
    }

    /**
     * 取并校验扩展名。
     *
     * @param originalFilename 上传时的原始文件名
     * @return 小写扩展名，不在白名单内返回 空
     */
    private String resolveExtension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            return null;
        }
        String lowered = extension.trim().toLowerCase(Locale.ROOT);
        List<String> allowed = uploadProperties.getAllowedExtensions();
        return allowed != null && allowed.contains(lowered) ? lowered : null;
    }

    /**
     * 单文件大小上限（字节）。
     *
     * @return 上限字节数
     */
    private long maxBytes() {
        return uploadProperties.getMaxSizeMb() * 1024L * 1024L;
    }

    /**
     * 按文件扩展名映射响应类型，未命中时退回二进制语义。
     *
     * @param fileName 文件名
     * @return MediaType
     */
    private MediaType resolveMediaType(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        if (extension == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            case "txt", "md" -> MediaType.parseMediaType("text/plain;charset=UTF-8");
            case "pdf" -> MediaType.parseMediaType("application/pdf");
            case "json" -> MediaType.APPLICATION_JSON;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
