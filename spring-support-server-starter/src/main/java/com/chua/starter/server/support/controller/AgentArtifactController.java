package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.service.AgentArtifactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 构件投放控制器。
 *
 * <p>给「目标主机上没有 arthas-boot.jar / skywalking-agent.jar」提供平台侧的自助通道：
 * 远程下载或指定平台本机路径，平台缓存后经 SFTP 投放到项目目标目录。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "Agent 构件投放")
@RestController
@RequestMapping("/ms/server/agent")
@RequiredArgsConstructor
public class AgentArtifactController {

    private final AgentArtifactService agentArtifactService;

    /**
     * 准备 Agent 构件并投放到项目目标主机。
     *
     * @param id        项目标识
     * @param kind      构件类型：arthas / skywalking / platform
     * @param source    来源：url 远程下载、local 平台本机路径
     * @param value     下载地址或本机路径（与 url 二选一，value 优先）
     * @param url       下载地址（兼容只传 url 的调用方）
     * @param targetDir 目标目录（空则 &lt;项目路径&gt;/lib/&lt;类型&gt;）
     * @param fileName  远端文件名（空则沿用源文件名）
     * @param force     远程下载是否忽略缓存
     * @return 投放结果（含 remotePath）
     */
    @Operation(summary = "下载或选取 Agent 构件并投放到目标主机")
    @PostMapping("/project/{id}/prepare")
    public ReturnResult<Map<String, Object>> prepare(
            @PathVariable Long id,
            @RequestParam(defaultValue = "arthas") String kind,
            @RequestParam(defaultValue = "url") String source,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String targetDir,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) Boolean force) {
        String artifact = value != null && !value.isBlank() ? value : url;
        return agentArtifactService.prepare(id, kind, source, artifact, targetDir, fileName,
                Boolean.TRUE.equals(force));
    }
}
