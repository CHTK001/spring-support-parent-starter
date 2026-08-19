package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.service.AiManufacturerFacadeService;
import com.chua.starter.ai.support.service.AiModelPriceSyncService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * AI 厂商管理控制器。
 * <p>
 * 提供 AI 模型厂商的增删改查、工厂列表查询以及 Logo 上传下载接口。
 *
 * @since 2026/04/03
 * @author CH
 */
@Tag(name = "AI Manufacturer Controller", description = "AI 厂商管理相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/manufacturer")
public class AiManufacturerController {

    /**
     * 厂商门面服务
     */
    private final AiManufacturerFacadeService aiManufacturerFacadeService;

    /**
     * 模型价格同步服务
     */
    private final AiModelPriceSyncService aiModelPriceSyncService;

    /**
     * 运行目录（用于 Logo 文件存储）
     */
    @Value("${user.dir:.}")
    /** 用户目录 */
    private String userDir;

    @Permission("admin")
    @Operation(summary = "获取厂商列表")
    @GetMapping("list")
    public ReturnResult<?> list() {
        return ReturnResult.ok(aiManufacturerFacadeService.list());
    }

    @Permission("admin")
    @Operation(summary = "获取支持的工厂列表")
    @GetMapping("factories")
    public ReturnResult<?> factories() {
        List<String> names = new ArrayList<>(ServiceProvider.of(ChatClient.class).getExtensions());
        if (!names.contains("siliconflow")) {
            names.add("siliconflow");
        }
        names.sort(Comparator.naturalOrder());
        return ReturnResult.ok(names);
    }

    @Permission("admin")
    @Operation(summary = "同步模型价格（从 ChatClient 获取并更新）")
    @PutMapping("update-model-prices")
    public ReturnResult<?> updateModelPrices(
            @Parameter(description = "厂商编码") @RequestParam String code) {
        aiModelPriceSyncService.syncPrices(code);
        return ReturnResult.success();
    }

    @Permission("admin")
    @Operation(summary = "替换厂商模型（从 API 拉取最新模型列表，先删后插，保留本地模型）")
    @PutMapping("replace-models")
    public ReturnResult<?> replaceModels(
            @Parameter(description = "厂商编码") @RequestParam String code) {
        aiModelPriceSyncService.replaceModels(code);
        return ReturnResult.success();
    }

    @Permission("admin")
    @Operation(summary = "从厂商 API 直拉模型列表并同步到模型表")
    @PutMapping("sync-models")
    public ReturnResult<?> syncModels(
            @Parameter(description = "厂商编码") @RequestParam String code) {
        int count = aiManufacturerFacadeService.syncModels(code);
        return ReturnResult.ok(count, "模型同步完成，共 " + count + " 个");
    }

    @Permission("admin")
    @Operation(summary = "保存厂商信息")
    @PostMapping("save")
    public ReturnResult<?> save(@Parameter(description = "厂商实体") @RequestBody SysAiManufacturer manufacturer) {
        return ReturnResult.ok(aiManufacturerFacadeService.save(manufacturer));
    }

    @Permission("admin")
    @Operation(summary = "更新厂商信息")
    @PutMapping("update")
    public ReturnResult<?> update(@Parameter(description = "厂商实体") @RequestBody SysAiManufacturer manufacturer) {
        return ReturnResult.of(aiManufacturerFacadeService.update(manufacturer));
    }

    @Permission("admin")
    @Operation(summary = "删除厂商")
    @DeleteMapping("delete")
    public ReturnResult<?> delete(@Parameter(description = "厂商标识") @RequestParam Integer sysAiManufacturerId) {
        return ReturnResult.of(aiManufacturerFacadeService.delete(sysAiManufacturerId));
    }

    @Permission("admin")
    @Operation(summary = "上传厂商标志")
    @SneakyThrows
    @PostMapping(value = "upload-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<?> uploadLogo(@Parameter(description = "标志文件") @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.illegal("文件不能为空");
        }
        // 文件大小限制：不超过 2MB，防止打满磁盘
        if (file.getSize() > 2 * 1024 * 1024) {
            return ReturnResult.illegal("图片大小不能超过 2MB");
        }
        // 扩展名白名单：禁止 SVG（可执行脚本导致存储型 XSS）及其它危险类型
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_LOGO_EXTENSIONS.contains(extension)) {
            return ReturnResult.illegal("仅支持上传 png/jpg/jpeg/gif/webp/ico 格式图片");
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path folder = Paths.get(userDir, ".runtime", "ai", "manufacturer", "logo");
        Files.createDirectories(folder);
        Path target = folder.resolve(storedName);
        file.transferTo(target);
        return ReturnResult.ok("/v2/manufacturer/logo/" + storedName);
    }

    @Operation(summary = "获取厂商标志")
    @SneakyThrows
    @GetMapping(value = "logo/{fileName:.+}")
    public ResponseEntity<Resource> logo(@Parameter(description = "文件名称") @PathVariable String fileName) {
        Path folder = Paths.get(userDir, ".runtime", "ai", "manufacturer", "logo").toAbsolutePath().normalize();
        Path file = folder.resolve(fileName).normalize();
        // 防路径穿越：解析后的文件必须位于 logo 目录内
        if (!file.startsWith(folder) || !Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toUri());
        String contentType = Files.probeContentType(file);
        // SVG 等可执行脚本类型强制附件下载，防止浏览器直接执行（存储型 XSS 防护）
        boolean dangerous = contentType != null && contentType.toLowerCase(Locale.ROOT).contains("svg");
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        dangerous ? "attachment; filename=\"" + fileName + "\"" : "inline")
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .body(resource);
    }

    /**
     * 允许上传的厂商标志扩展名白名单。
     */
    private static final Set<String> ALLOWED_LOGO_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico"
    );
}

