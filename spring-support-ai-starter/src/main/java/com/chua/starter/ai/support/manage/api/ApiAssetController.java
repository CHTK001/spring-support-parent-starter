package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 静态资源控制器 - 提供模型/平台 Logo SVG
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/assets")
public class ApiAssetController {

    /**
     * Logo 资源。前端（agentLogo.ts）请求带子目录的多段路径（如 brand/nomi.svg、
     * tools/coding/codex.svg），单段 {name} 无法匹配会导致 404 裂图，这里用
     * /logos/** 接住全部层级；classpath 无对应文件时返回首字母兜底 SVG。
     */
    @GetMapping("/logos/**")
    public ResponseEntity<Resource> getLogo(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        String name = uri.substring(uri.indexOf("/logos/") + "/logos/".length());
        try {
            name = java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 保持原始路径
        }
        // 去掉可能的查询参数与开头的斜杠
        name = name.split("\\?")[0];
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        String base = name.toLowerCase().replace(" ", "-");
        // 依次尝试原始多段路径与仅文件名两种 classpath 位置
        String[] candidates = {
                "static/logos/" + base,
                "static/logos/" + base.substring(base.lastIndexOf('/') + 1),
                "assets/logos/" + base,
        };
        for (String candidate : candidates) {
            try {
                Resource resource = new ClassPathResource(candidate);
                if (resource.exists()) {
                    return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml")
                        .body(resource);
                }
            } catch (Exception e) {
                log.warn("Logo not found: {}", candidate);
            }
        }
        // 兜底 SVG 用文件名（去掉目录与扩展名）生成首字母图标
        String displayName = base.substring(base.lastIndexOf('/') + 1);
        if (displayName.endsWith(".svg")) {
            displayName = displayName.substring(0, displayName.length() - 4);
        }
        if (displayName.endsWith(".png")) {
            displayName = displayName.substring(0, displayName.length() - 4);
        }
        String svg = buildDefaultSvg(displayName);
        byte[] bytes = svg.getBytes();
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml")
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length))
            .body(resource);
    }

    private String buildDefaultSvg(String name) {
        String initial = name.substring(0, Math.min(2, name.length())).toUpperCase();
        // 按名称稳定取色，兜底图标也有可区分的品牌感
        String[] palette = {"#6366f1", "#0ea5e9", "#10b981", "#f59e0b", "#f43f5e", "#8b5cf6", "#06b6d4", "#f97316"};
        int h = 0;
        for (int i = 0; i < name.length(); i++) {
            h = (h * 31 + name.charAt(i)) & 0x7fffffff;
        }
        String color = palette[h % palette.length];
        String colorLight = color + "33";
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'>" +
               "<rect width='64' height='64' rx='14' fill='" + colorLight + "'/>" +
               "<rect x='4' y='4' width='56' height='56' rx='11' fill='none' stroke='" + color + "' stroke-width='2' stroke-dasharray='0'/>" +
               "<text x='32' y='43' text-anchor='middle' font-size='26' font-weight='700' fill='" + color + "' " +
               "font-family='-apple-system,Segoe UI,sans-serif'>" + initial + "</text></svg>";
    }
}
