package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.service.AgentDeployService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Agent 接收端
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ms/agent")
public class AgentController {

    /** 部署服务（提供 agent 包定位） */
    private final AgentDeployService deployService;

    /**
     * 注册。
     *
     * @param body 请求体，不允许为 null
     * @return Mono 对象
     */
    @PostMapping("/register")
    public Mono<ReturnResult<?>> register(@RequestBody Map<String, Object> body) {
        log.info("Agent registered: {}", body);
        return Mono.just(ReturnResult.success("ok"));
    }

    /**
     * metrics。
     *
     * @param body 请求体，不允许为 null
     * @return Mono 对象
     */
    @PostMapping("/metrics")
    public Mono<ReturnResult<?>> metrics(@RequestBody Map<String, Object> body) {
        return Mono.just(ReturnResult.success("ok"));
    }

    /**
     * hostinfo。
     *
     * @param body 请求体，不允许为 null
     * @return Mono 对象
     */
    @PostMapping("/hostinfo")
    public Mono<ReturnResult<?>> hostinfo(@RequestBody Map<String, Object> body) {
        log.info("Agent hostinfo: {}", body);
        return Mono.just(ReturnResult.success("ok"));
    }

    /**
     * 下载 agent 安装包（部署时由目标机自取）
     * <p>
     * Windows 走 WinRM 通道，单条命令行只有约 8KB，平台无法把 6MB 级的包推进去，
     * 只能让目标机按 monitor.agent.jar-url 反向拉取这个端点。
     * </p>
     *
     * @return agent 包字节流；定位不到包时 404
     */
    @GetMapping("/package")
    public ResponseEntity<Flux<DataBuffer>> packageFile() {
        Path artifact;
        long size;
        try {
            artifact = deployService.packageFile();
            size = Files.size(artifact);
        } catch (Exception e) {
            log.warn("[agent] 未定位到 agent 包: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
        Flux<DataBuffer> body = DataBufferUtils.read(artifact, new DefaultDataBufferFactory(), 8192)
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    /**
     * 下载 Agent tar.gz
     * 结构: monitor-agent.jar + lib/*.jar + agent.yml + start.sh
     * @param serverUrl 服务端URL，不允许为 null
     * @return 响应实体 对象
     */
    @GetMapping("/download")
    public ResponseEntity<FileSystemResource> download(@RequestParam(defaultValue = "http://localhost:8081") String serverUrl) throws IOException {
        // 更新配置
        File distDir = new File("../../spring-api-starter/spring-api-support-monitor-agent/target");
        File cfgFile = new File(distDir, "agent.yml");
        String cfg = "server:\n  url: " + serverUrl + "\nagent:\n  port: 19876\n  token: default-token\n";
        Files.writeString(cfgFile.toPath(), cfg);

        // 打 tar.gz
        File tarFile = File.createTempFile("monitor-agent-", ".tar.gz");
        try (FileOutputStream fos = new FileOutputStream(tarFile);
             java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(fos)) {
            addToTar(gz, distDir, "monitor-agent");
        }

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        h.setContentDispositionFormData("attachment", "monitor-agent.tar.gz");
        return ResponseEntity.ok().headers(h).body(new FileSystemResource(tarFile));
    }

    /**
     * 添加转为Tar。
     *
     * @param os 方法入参 os
     * @param dir 目录，不允许为 null
     * @param base 方法入参 base
     * @throws IOException 当执行过程不满足前置条件时
     */
    private void addToTar(OutputStream os, File dir, String base) throws IOException {
        // 简化：用 tar 命令
        try {
            Process p = new ProcessBuilder("tar", "-czf", "-", "-C", dir.getParent(), dir.getName())
                    .redirectErrorStream(true).start();
            p.getInputStream().transferTo(os);
            p.waitFor();
        } catch (Exception e) {
            log.error("tar failed", e);
        }
    }
}
