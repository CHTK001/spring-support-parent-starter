package com.chua.spring.support.email.controller;

import com.chua.spring.support.email.entity.EmailAccount;
import com.chua.spring.support.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 邮箱账户管理控制器
 * 
 * @author CH
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final EmailService emailService;

    /**
     * 获取账户列表
     */
    @GetMapping("/list")
    public Map<String, Object> getAccountList() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", new ArrayList<>());
        return result;
    }

    /**
     * 添加账户
     */
    @PostMapping("/add")
    public Map<String, Object> addAccount(@RequestBody EmailAccount account) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "账户添加成功");
        result.put("data", account);
        return result;
    }

    /**
     * 更新账户
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateAccount(
            @PathVariable String id,
            @RequestBody EmailAccount account) {
        account.setId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "账户更新成功");
        return result;
    }

    /**
     * 删除账户
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteAccount(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "账户删除成功");
        return result;
    }

    /**
     * 测试连接
     */
    @PostMapping("/test")
    public Map<String, Object> testConnection(@RequestBody EmailAccount account) {
        try {
            boolean success = emailService.testConnection(account);
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "连接测试成功" : "连接测试失败");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "连接测试失败: " + e.getMessage());
            return result;
        }
    }
}
