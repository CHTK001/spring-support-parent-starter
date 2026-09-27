package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.FileAssetContentVO;
import com.chua.starter.monitor.support.model.FileAssetItemVO;
import com.chua.starter.monitor.support.service.FileAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件资产 REST 接口
 *
 * <p>前端「文件资产」页面通过本接口读写 {@code FileStorage} 存储桶，与服务管理启动的
 * HTTP 文件服务器共用同一后端。浏览器不再直连文件服务器端口，避免地址硬编码与跨源限制。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "文件资产浏览与读写")
@RestController
@RequestMapping("/ms/monitor/file-assets")
public class FileAssetController {

    /**
     * 文件资产服务
     */
    private final FileAssetService fileAssetService;

    /**
     * 构造方法。
     *
     * @param fileAssetService 文件资产服务
     */
    public FileAssetController(FileAssetService fileAssetService) {
        this.fileAssetService = fileAssetService;
    }

    /**
     * 列出可用存储桶。
     *
     * @return 存储桶名称列表
     */
    @Operation(summary = "列出文件存储桶")
    @GetMapping("/buckets")
    public ReturnResult<List<String>> buckets() {
        return call(fileAssetService::buckets);
    }

    /**
     * 列出目录内容。
     *
     * @param bucket 存储桶，为空取默认存储
     * @param path   目录路径，为空表示根目录
     * @param limit  最多返回条数
     * @return 条目列表
     */
    @Operation(summary = "列出文件资产")
    @GetMapping("/list")
    public ReturnResult<List<FileAssetItemVO>> list(@RequestParam(required = false) String bucket,
                                                    @RequestParam(required = false) String path,
                                                    @RequestParam(defaultValue = "500") int limit) {
        return call(() -> fileAssetService.list(bucket, path, limit));
    }

    /**
     * 上传文件。
     *
     * @param bucket 存储桶，为空取默认存储
     * @param path   目标目录，为空表示根目录
     * @param file   上传的文件
     * @return 写入后的条目
     */
    @Operation(summary = "上传文件资产")
    @PostMapping("/upload")
    public ReturnResult<FileAssetItemVO> upload(@RequestParam(required = false) String bucket,
                                                @RequestParam(required = false) String path,
                                                @RequestParam("file") MultipartFile file) {
        try {
            FileAssetItemVO item = fileAssetService.upload(bucket, path,
                    file.getOriginalFilename(), file.getInputStream(), file.getSize());
            return ReturnResult.ok(item);
        } catch (IOException e) {
            log.warn("文件资产上传失败 path={} name={}", path, file.getOriginalFilename(), e);
            return ReturnResult.error("上传失败: " + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 删除文件或目录。
     *
     * @param bucket 存储桶，为空取默认存储
     * @param path   对象路径
     * @return 是否删除成功
     */
    @Operation(summary = "删除文件资产")
    @DeleteMapping
    public ReturnResult<Boolean> delete(@RequestParam(required = false) String bucket,
                                        @RequestParam String path) {
        return call(() -> fileAssetService.delete(bucket, path));
    }

    /**
     * 下载文件，直接写出字节流。
     *
     * @param bucket   存储桶，为空取默认存储
     * @param path     对象路径
     * @param inline   是否内联预览，默认作为附件下载
     * @param response 响应
     */
    @Operation(summary = "下载文件资产")
    @GetMapping("/download")
    public void download(@RequestParam(required = false) String bucket,
                         @RequestParam String path,
                         @RequestParam(defaultValue = "false") boolean inline,
                         HttpServletResponse response) {
        try {
            FileAssetContentVO content = fileAssetService.download(bucket, path);
            try (InputStream in = content.content()) {
                response.setContentType(content.contentType());
                if (content.contentLength() >= 0) {
                    response.setContentLengthLong(content.contentLength());
                }
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition(content.fileName(), inline));
                in.transferTo(response.getOutputStream());
                response.flushBuffer();
            }
        } catch (IOException e) {
            log.warn("文件资产下载失败 bucket={} path={}", bucket, path, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException | IllegalStateException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * 构造内容处置头。
     *
     * <p>{@code filename} 回退值必须是 ASCII——含非 Latin-1 字符时容器会整个丢弃该响应头，
     * 中文名走 {@code filename*}（RFC 5987）。</p>
     *
     * @param fileName 文件名
     * @param inline   是否内联
     * @return 头值
     */
    private String disposition(String fileName, boolean inline) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String fallback = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
        return (inline ? "inline" : "attachment") + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 执行查询类操作，把异常统一成失败响应。
     *
     * @param action 操作
     * @param <T>    返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(java.util.function.Supplier<T> action) {
        try {
            return ReturnResult.ok(action.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}
