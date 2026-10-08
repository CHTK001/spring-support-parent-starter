package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 会话工作目录的**文件读取**端点（文本 / 原始字节）。
 *
 * <p>背景（2026-10-08）：前端文本预览走 {@code /v2/sessions/{id}/workspace/file}、
 * 图片预览走 {@code .../workspace/raw-file}，但后端只实现了 {@code /workspace/tree} ——
 * 这两个端点恒 404，所以「文本文件无法预览」（用户报障 2.1）。</p>
 *
 * <p>单独成一个控制器：同样的方法原先加在 {@code AiSessionController} 里，部署后
 * 运行时却始终 404（同类的 tree 正常），怀疑旧类/缓存阴影，故独立成类规避。</p>
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class AiWorkspaceFileController {

    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 读取工作目录内的文本文件（前端文本预览）。
     *
     * @param id   会话标识（仅用于路由，工作目录按账号解析，与 tree 同口径）
     * @param path 相对工作目录的路径，或工作目录内的绝对路径
     * @return {content, extension, name, size}
     */
    @GetMapping("/{id}/workspace/file")
    public ReturnResult<Map<String, Object>> workspaceFile(@PathVariable("id") String id,
                                                          @RequestParam("path") String path) {
        Path root = resolveAgentWorkspace();
        if (root == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "工作目录未配置");
        }
        Path target = root.resolve(path == null ? "" : path).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "路径越界");
        }
        if (!Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        try {
            long size = Files.size(target);
            if (size > 2L * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件过大（上限 2MB）");
            }
            String name = target.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot) : "";
            Map<String, Object> data = new LinkedHashMap<>(4);
            data.put("content", Files.readString(target, StandardCharsets.UTF_8));
            data.put("extension", ext);
            data.put("name", name);
            data.put("size", size);
            return ReturnResult.ok(data);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取失败: " + e.getMessage());
        }
    }

    /**
     * 读取工作目录内的原始字节（图片等二进制预览）。
     *
     * @param id   会话标识
     * @param path 相对/绝对路径（越界与不存在返回 404）
     * @return 原始字节 + 按后缀推断的 Content-Type
     */
    @GetMapping("/{id}/workspace/raw-file")
    public ResponseEntity<byte[]> workspaceRawFile(@PathVariable("id") String id,
                                                   @RequestParam("path") String path) {
        Path root = resolveAgentWorkspace();
        if (root == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        Path target = root.resolve(path == null ? "" : path).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            String lower = target.getFileName().toString().toLowerCase();
            String ct = lower.endsWith(".png") ? "image/png"
                    : (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ? "image/jpeg"
                    : lower.endsWith(".gif") ? "image/gif"
                    : lower.endsWith(".webp") ? "image/webp"
                    : lower.endsWith(".svg") ? "image/svg+xml"
                    : "application/octet-stream";
            return ResponseEntity.ok().header("Content-Type", ct).body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 解析当前账号的工作目录（{@code <基路径>/<账号标识>/agent}），口径与
     * {@code AiSessionController#workspaceTree} 完全一致。
     *
     * @return 工作目录；未配置或越界返回 null
     */
    private Path resolveAgentWorkspace() {
        String base;
        try {
            base = globalSettingService.getValue("system", "workspaceBaseDir").orElse(null);
        } catch (Exception e) {
            log.warn("[WsFile] 读取工作目录基路径失败: {}", e.getMessage());
            return null;
        }
        if (base == null || base.isBlank()) {
            return null;
        }
        try {
            Path basePath = Paths.get(base.trim()).toAbsolutePath().normalize();
            String account = Objects.toString(AiUserContext.currentUserId(), "public")
                    .replaceAll("[^A-Za-z0-9_-]", "_");
            Path workspace = basePath.resolve(account).resolve("agent").normalize();
            if (!workspace.startsWith(basePath)) {
                log.warn("[WsFile] 工作目录越界，已拒绝: {}", workspace);
                return null;
            }
            return workspace;
        } catch (Exception e) {
            log.warn("[WsFile] 解析工作目录失败 base={}: {}", base, e.getMessage());
            return null;
        }
    }
}
