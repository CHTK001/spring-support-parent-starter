package com.chua.starter.eduai.dimension.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 学生头像上传 / 预览。
 *
 * <p>为什么单独一个控制器，而不是塞进 {@code /v2/edu/dimension/**}：
 * 那只控制器带类级 {@code @Permission("admin")}，而头像要在 {@code <img>} 里直接显示，
 * {@code <img>} **无法携带 Authorization 头** —— 如果预览端点受权限拦截，
 * 浏览器取图必然 403，头像永远显示不出来，而且报错只出现在图片请求里、很隐蔽。</p>
 *
 * <p>因此这里把两件事分开：
 * <ul>
 *   <li><b>上传</b>（写）：方法级 {@code @Permission("admin")}，仅管理员。</li>
 *   <li><b>预览</b>（读）：不加权限注解，并在 {@code plugin.oauth.whitelist} 里放行
 *       {@code /v2/edu/student-avatar/file}，让 {@code <img src>} 能直接取到。</li>
 * </ul>
 * 预览路径只接受 {@code data/student-avatars} 下的**相对路径**并做穿越校验，
 * 文件名是随机 UUID、扩展名走白名单，故放行不构成任意文件读取。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/student-avatar")
@Tag(name = "教育AI-学生头像")
public class EduStudentAvatarController {

    /**
     * 允许上传的图片扩展名（白名单，防任意类型落盘）
     */
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");

    /**
     * 头像根目录
     *
     * <p>与知识点附件（data/knowledge-files）分开存放：两者生命周期与清理策略不同，
     * 混在一起会让「删学生头像」变成「扫知识点附件目录」。</p>
     */
    private static final Path FILE_ROOT = Paths.get("data", "student-avatars");

    /**
     * 上传学生头像，返回相对路径供 saveStudent 绑定到 studentAvatar。
     *
     * @param file 头像图片
     * @return 相对路径、文件名、扩展名、大小
     * @throws IOException 落盘失败
     */
    @Operation(summary = "上传学生头像")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Permission("admin")
    public ReturnResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("文件为空");
        }
        String original = file.getOriginalFilename() == null ? "avatar" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) {
            ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            return ReturnResult.error("仅支持 png / jpg / jpeg / gif / webp / bmp 图片");
        }
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = FILE_ROOT.resolve(day);
        Files.createDirectories(dir);
        Path target = dir.resolve(stored);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String relative = day + "/" + stored;
        Map<String, Object> body = new HashMap<>(4);
        body.put("filePath", relative);
        body.put("fileName", original);
        body.put("fileType", ext);
        body.put("fileSize", Files.size(target));
        log.info("学生头像上传成功: {}", relative);
        return ReturnResult.ok(body);
    }

    /**
     * 按相对路径读取头像字节流（{@code <img>} 直链显示）。
     *
     * @param path data/student-avatars 下的相对路径
     * @param response 响应
     * @throws IOException 读取失败
     */
    @Operation(summary = "预览学生头像")
    @GetMapping("/file")
    public void file(@RequestParam String path, HttpServletResponse response) throws IOException {
        Path normalized = Paths.get(path).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "非法路径");
            return;
        }
        Path full = FILE_ROOT.resolve(normalized).normalize();
        if (!full.startsWith(FILE_ROOT.normalize()) || !Files.isRegularFile(full)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        String name = full.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        response.setContentType(switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        });
        response.setHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
        try (InputStream in = Files.newInputStream(full); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
