package com.chua.starter.ai.support.nomifun.api;

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
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'>" +
               "<rect width='64' height='64' rx='12' fill='#6366f1'/>" +
               "<text x='32' y='44' text-anchor='middle' font-size='24' font-weight='bold' fill='white'>" +
               initial + "</text></svg>";
    }
}
