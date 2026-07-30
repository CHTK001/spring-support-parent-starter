package com.chua.spring.support.email.controller;

import com.chua.spring.support.email.entity.EmailAccountExport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 分组管理控制器
 * 
 * @author CH
 */
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    /**
     * 获取分组列表
     */
    @GetMapping("/list")
    public Map<String, Object> getGroupList() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", new ArrayList<>());
        return result;
    }

    /**
     * 创建分组
     */
    @PostMapping("/create")
    public Map<String, Object> createGroup(@RequestBody EmailAccountExport.GroupData group) {
        // 生成 ID
        group.setId(UUID.randomUUID().toString());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "分组创建成功");
        result.put("data", group);
        return result;
    }

    /**
     * 更新分组
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateGroup(
            @PathVariable String id,
            @RequestBody EmailAccountExport.GroupData group) {
        group.setId(id);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "分组更新成功");
        result.put("data", group);
        return result;
    }

    /**
     * 删除分组
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteGroup(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "分组删除成功");
        return result;
    }

    /**
     * 获取分组下的账户
     */
    @GetMapping("/{id}/accounts")
    public Map<String, Object> getGroupAccounts(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", new ArrayList<>());
        return result;
    }

    /**
     * 批量设置账户分组
     */
    @PostMapping("/batch-set")
    public Map<String, Object> batchSetGroup(@RequestBody Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        List<String> accountIds = (List<String>) data.get("accountIds");
        String groupId = (String) data.get("groupId");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "批量设置成功，共 " + accountIds.size() + " 个账户");
        return result;
    }
}
