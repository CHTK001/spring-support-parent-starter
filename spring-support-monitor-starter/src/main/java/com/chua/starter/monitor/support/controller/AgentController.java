package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;

/**
 * Agent 接收端
 */
@Slf4j
@RestController
@RequestMapping("/ms/agent")
public class AgentController {

    @PostMapping("/register")
    public Mono<ReturnResult<?>> register(@RequestBody Map<String, Object> body) {
        log.info("Agent registered: {}", body);
        return Mono.just(ReturnResult.success("ok"));
    }

    @PostMapping("/metrics")
    public Mono<ReturnResult<?>> metrics(@RequestBody Map<String, Object> body) {
        return Mono.just(ReturnResult.success("ok"));
    }

    @PostMapping("/hostinfo")
    public Mono<ReturnResult<?>> hostinfo(@RequestBody Map<String, Object> body) {
        log.info("Agent hostinfo: {}", body);
        return Mono.just(ReturnResult.success("ok"));
    }

    /**
    * 下载 Agent tar.gz
    * 结构: monitor-agent.jar + lib/*.jar + agent.yml + start.sh
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
