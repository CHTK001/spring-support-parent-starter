package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.service.SysAiRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 角色（助手）头像控制器。
 *
 * <p>提供头像上传与读取：头像文件以角色主键命名落盘到本地目录（默认
 * {@code ./data/avatars/characters}），访问路径统一为 {@code /v2/characters/{id}/avatar}。
 * 该路径在开发态经前端代理、在独立部署态由后端直接提供，均可达。</p>
 *
 * @since 4.0.0.42
 *
 * @author CH
 */
@Slf4j
@Tag(name = "AI - 角色头像")
@RestController
@RequestMapping("/v2/characters")
@RequiredArgsConstructor
public class CharacterAvatarController {

    /**
     * 通用助手编码（系统内置，按 code 定位，不依赖主键）。
     */
    private static final String GENERAL_ASSISTANT_CODE = "general-assistant";

    /**
     * 角色服务
     */
    private final SysAiRoleService roleService;

    /**
     * 头像存储目录（相对路径解析到进程工作目录）。
     */
    @Value("${ai.character.avatar-dir:./data/avatars/characters}")
    private String avatarDir;

    /**
     * 上传（更新）角色头像。
     *
     * <p>2026-10-14 新增通用助手保护：code=general-assistant（「通用助手」）
     * 头像由产品统一维护，不开放上传 —— 直接 403，前端同样拦截（character.api
     * updateCharacter 锁 avatarUrl/avatarFile），但接口必须自己兜底，
     * 防止绕过前端直接 POST。</p>
     *
     * <p>2026-11-14 改为按 code 判定：种子角色（三国角色库等）会占用 id=1，
     * 按主键判定会让普通角色的头像永远传不上去。</p>
     *
     * @param id     角色主键
     * @param avatar 头像图片文件
     * @return 头像访问信息（含 url）
     */
    @Operation(summary = "上传角色头像")
    @PostMapping("/{id}/avatars")
    public Map<String, Object> uploadAvatar(@PathVariable Integer id,
                                            @RequestParam("avatar") MultipartFile avatar) throws IOException {
        if (isGeneralAssistant(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "通用助手头像由产品统一维护，不允许上传");
        }
        if (avatar == null || avatar.isEmpty()) {
            throw new IllegalArgumentException("头像文件为空");
        }
        final SysAiRole role = roleService.getById(id);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在: " + id);
        }

        final String ext = resolveExtension(avatar);
        final Path dir = Files.createDirectories(avatarDirectory());
        deleteOldFiles(dir, id);

        final Path target = dir.resolve(id + ext);
        avatar.transferTo(target.toFile());

        final String servedPath = "/v2/characters/" + id + "/avatar";
        role.setAvatar(servedPath);
        roleService.update(role);

        final String url = servedPath + "?t=" + System.currentTimeMillis();
        log.info("[CharacterAvatar] 角色 {} 头像已更新: {}", id, target);

        final Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(id));
        result.put("name", avatar.getOriginalFilename());
        result.put("url", url);
        result.put("size", avatar.getSize());
        result.put("type", avatar.getContentType());
        return result;
    }

    /**
     * 读取角色头像（二进制）。
     *
     * <p>2026-10-14 新增通用助手保护：code=general-assistant（「通用助手」）
     * 头像由产品统一维护，不开放上传/删除。这里对读取做兜底 —— 即使头像文件
     * 或 avatar 字段缺失，也不 404，前端 Avatar 组件拿到空会显示首字占位，
     * 产品 logo 由前端静态资源统一提供（与上传链路解耦）。</p>
     *
     * @param id 角色主键
     * @return 图片字节流
     * @throws IOException 异常
     */
    @Operation(summary = "读取角色头像")
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Integer id) throws IOException {
        final Path dir = avatarDirectory();
        final Path file = findFile(dir, id);
        if (file == null || !Files.exists(file)) {
            // 即使头像文件（或 avatar 字段）缺失，也返回 200 空体，不 404：
            // 前端 Avatar 组件 onerror 回退到首字占位（见 CardAvatar.vue）。
            return ResponseEntity.ok().build();
        }
        final byte[] bytes = Files.readAllBytes(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(probeContentType(file)))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(bytes);
    }

    /**
     * 删除角色头像（恢复默认首字头像）。
     *
     * <p>2026-10-14 新增通用助手保护：通用助手头像由产品统一维护，不开放删除 ——
     * 直接 403（与上传接口同口径）。2026-11-14 起按 code 判定，不再看主键 1。</p>
     *
     * @param id 角色主键
     * @return 操作结果
     */
    @Operation(summary = "删除角色头像")
    @PostMapping("/{id}/avatar/clear")
    public Map<String, Object> clearAvatar(@PathVariable Integer id) {
        if (isGeneralAssistant(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "通用助手头像由产品统一维护，不允许删除");
        }
        final SysAiRole role = roleService.getById(id);
        if (role != null) {
            role.setAvatar(null);
            roleService.update(role);
        }
        try {
            deleteOldFiles(avatarDirectory(), id);
        } catch (IOException e) {
            log.warn("[CharacterAvatar] 删除头像文件失败 id={}: {}", id, e.getMessage());
        }
        final Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * 是否为内置通用助手（code=general-assistant）。
     *
     * <p>只认 code、不认主键：种子角色（三国角色库等）自增主键同样会占用小 id，
     * 按 id 判定会把普通角色误当成通用助手。</p>
     *
     * @param id 角色主键
     * @return 是通用助手返回 {@code true}
     */
    private boolean isGeneralAssistant(Integer id) {
        if (id == null) {
            return false;
        }
        final SysAiRole role = roleService.getById(id);
        return role != null && GENERAL_ASSISTANT_CODE.equals(role.getCode());
    }

    /**
     * 头像目录（解析为绝对路径）。
     * @return 返回值
    */
    private Path avatarDirectory() {
        return Paths.get(avatarDir).toAbsolutePath().normalize();
    }

    /**
     * 删除该角色既有头像（任意扩展名）。
     * @param dir 路径
     * @param id 整数
     * @return 返回值
     * @throws IOException 异常
    */
    private void deleteOldFiles(Path dir, Integer id) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(id + "."))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("[CharacterAvatar] 删除旧头像失败 {}: {}", p, e.getMessage());
                        }
                    });
        }
    }

    /**
     * 查找角色头像文件。
     * @param dir 路径
     * @param id 整数
     * @return 返回值
     * @throws IOException 异常
    */
    private Path findFile(Path dir, Integer id) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith(id + "."))
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        }
    }

    /**
     * 根据上传文件解析扩展名（含点，默认 .png）。
     * @param file 参数
     * @return 返回值
    */
    private String resolveExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            String suffix = name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (suffix.matches("\\.(png|jpe?g|gif|webp|svg|bmp|ico)")) {
                return suffix.equals(".jpeg") ? ".jpg" : suffix;
            }
        }
        String contentType = file.getContentType();
        if (contentType != null) {
            switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg": return ".jpg";
                case "image/gif": return ".gif";
                case "image/webp": return ".webp";
                case "image/svg+xml": return ".svg";
                default: return ".png";
            }
        }
        return ".png";
    }

    /**
     * 探测文件内容类型（失败回退 image/png）。
     * @param file 路径
     * @return 返回值
    */
    private String probeContentType(Path file) {
        try {
            String type = Files.probeContentType(file);
            if (type != null) {
                return type;
            }
        } catch (IOException ignored) {
            // fall through
        }
        return MediaType.IMAGE_PNG_VALUE;
    }
}
