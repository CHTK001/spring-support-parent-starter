package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.service.ArthasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Arthas 诊断工具管理控制器。
 *
 * <p>提供 Arthas 运行时 attach / detach / 命令执行 / 终端输出等管理接口。</p>
 *
 * @author CH
 */
@Tag(name = "Arthas 管理")
@RestController
@RequestMapping("/ms/server/arthas")
@RequiredArgsConstructor
public class ArthasController {

    private final ArthasService arthasService;

    @Operation(summary = "列出发布下所有 Java 项目的 Arthas 状态")
    @GetMapping("/projects")
    public ReturnResult<List<Map<String, Object>>> listArthasProjects(@RequestParam Long releaseId) {
        return arthasService.listArthasProjects(releaseId);
    }

    @Operation(summary = "查询单个项目的 Arthas 状态")
    @GetMapping("/project/{id}/status")
    public ReturnResult<Map<String, Object>> getArthasStatus(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return arthasService.getArthasStatus(id);
    }

    @Operation(summary = "运行时挂载 Arthas 到目标 JVM")
    @PostMapping("/project/{id}/attach")
    public ReturnResult<String> attachArthas(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestParam(required = false) String arthasJarPath,
            @RequestParam(required = false) Integer telnetPort) {
        return arthasService.attachArthas(id, arthasJarPath, telnetPort);
    }

    @Operation(summary = "从 JVM 卸载 Arthas")
    @PostMapping("/project/{id}/detach")
    public ReturnResult<String> detachArthas(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return arthasService.detachArthas(id);
    }

    @Operation(summary = "在 Arthas 终端执行命令")
    @PostMapping("/project/{id}/exec")
    public ReturnResult<String> execArthasCommand(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestParam String command) {
        return arthasService.execArthasCommand(id, command);
    }

    @Operation(summary = "获取 Arthas 终端输出")
    @GetMapping("/project/{id}/console")
    public ReturnResult<String> getArthasConsole(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestParam(defaultValue = "50") int lines) {
        return arthasService.getArthasConsole(id, lines);
    }
}
