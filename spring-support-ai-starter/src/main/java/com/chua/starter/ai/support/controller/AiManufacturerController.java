package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.service.AiManufacturerFacadeService;
import com.chua.starter.ai.support.service.AiModelPriceSyncService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * AI 厂商管理控制器。
 * <p>
 * 提供 AI 模型厂商的增删改查、工厂列表查询以及 Logo 上传下载接口。
 *
 * @author CH
 * @since 2026/04/03
 */
@Tag(name = "AI Manufacturer Controller", description = "AI 厂商管理相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/ai/manufacturer")
public class AiManufacturerController {

    private final AiManufacturerFacadeService aiManufacturerFacadeService;
    private final AiModelPriceSyncService aiModelPriceSyncService;

    @Value("${user.dir:.}")
    private String userDir;

    @Operation(summary = "获取厂商列表")
    @GetMapping("list")
    public ReturnResult<?> list() {
        return ReturnResult.ok(aiManufacturerFacadeService.list());
    }

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

    @Operation(summary = "同步模型价格（从 ChatClient 获取并更新）")
    @PutMapping("update-model-prices")
    public ReturnResult<?> updateModelPrices(
            @Parameter(description = "厂商编码") @RequestParam String code) {
        aiModelPriceSyncService.syncPrices(code);
        return ReturnResult.success();
    }

    @Operation(summary = "替换厂商模型（从 API 拉取最新模型列表，先删后插，保留本地模型）")
    @PutMapping("replace-models")
    public ReturnResult<?> replaceModels(
            @Parameter(description = "厂商编码") @RequestParam String code) {
        aiModelPriceSyncService.replaceModels(code);
        return ReturnResult.success();
    }

    @Operation(summary = "保存厂商信息")
    @PostMapping("save")
    public ReturnResult<?> save(@Parameter(description = "厂商实体") @RequestBody SysAiManufacturer manufacturer) {
        return ReturnResult.ok(aiManufacturerFacadeService.save(manufacturer));
    }

    @Operation(summary = "更新厂商信息")
    @PutMapping("update")
    public ReturnResult<?> update(@Parameter(description = "厂商实体") @RequestBody SysAiManufacturer manufacturer) {
        return ReturnResult.of(aiManufacturerFacadeService.update(manufacturer));
    }

    @Operation(summary = "删除厂商")
    @DeleteMapping("delete")
    public ReturnResult<?> delete(@Parameter(description = "厂商标识") @RequestParam Integer sysAiManufacturerId) {
        return ReturnResult.of(aiManufacturerFacadeService.delete(sysAiManufacturerId));
    }

    @Operation(summary = "上传厂商标志")
    @SneakyThrows
    @PostMapping(value = "upload-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<?> uploadLogo(@Parameter(description = "标志文件") @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.illegal("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path folder = Paths.get(userDir, ".runtime", "ai", "manufacturer", "logo");
        Files.createDirectories(folder);
        Path target = folder.resolve(storedName);
        file.transferTo(target);
        return ReturnResult.ok("/v2/ai/manufacturer/logo/" + storedName);
    }

    @Operation(summary = "获取厂商标志")
    @SneakyThrows
    @GetMapping(value = "logo/{fileName:.+}")
    public ResponseEntity<Resource> logo(@Parameter(description = "文件名称") @PathVariable String fileName) {
        Path file = Paths.get(userDir, ".runtime", "ai", "manufacturer", "logo", fileName).normalize();
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
