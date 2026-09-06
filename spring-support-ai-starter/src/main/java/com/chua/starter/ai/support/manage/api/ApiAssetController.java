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

    @GetMapping("/logos/{name}")
    public ResponseEntity<Resource> getLogo(@PathVariable("name") String name) {
        String filename = name.toLowerCase().replace(" ", "-") + ".svg";
        try {
            Resource resource = new ClassPathResource("static/logos/" + filename);
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml")
                    .body(resource);
            }
        } catch (Exception e) {
            log.warn("Logo not found: {}", filename);
        }
        String svg = buildDefaultSvg(name);
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
