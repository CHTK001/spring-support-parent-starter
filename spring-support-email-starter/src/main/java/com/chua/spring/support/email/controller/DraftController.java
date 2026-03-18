package com.chua.spring.support.email.controller;

import com.chua.spring.support.email.entity.EmailDraft;
import com.chua.spring.support.email.service.DraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 草稿管理控制器
 * 
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/draft")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    /**
     * 保存草稿
     */
    @PostMapping("/save")
    public Map<String, Object> saveDraft(@RequestBody EmailDraft draft) {
        Map<String, Object> result = new HashMap<>();
        try {
            EmailDraft saved = draftService.saveDraft(draft);
            result.put("success", true);
            result.put("data", saved);
            result.put("message", "草稿保存成功");
        } catch (Exception e) {
            log.error("保存草稿失败", e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取草稿列表
     */
    @GetMapping("/list")
    public Map<String, Object> getDraftList(@RequestParam(required = false) String accountId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<EmailDraft> drafts = draftService.getDraftList(accountId);
            result.put("success", true);
            result.put("data", drafts);
        } catch (Exception e) {
            log.error("获取草稿列表失败", e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取草稿详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> getDraft(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            EmailDraft draft = draftService.getDraft(id);
            if (draft != null) {
                result.put("success", true);
                result.put("data", draft);
            } else {
                result.put("success", false);
                result.put("message", "草稿不存在");
            }
        } catch (Exception e) {
            log.error("获取草稿失败", e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除草稿
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteDraft(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean deleted = draftService.deleteDraft(id);
            result.put("success", deleted);
            result.put("message", deleted ? "删除成功" : "草稿不存在");
        } catch (Exception e) {
            log.error("删除草稿失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 批量删除草稿
     */
    @PostMapping("/delete-batch")
    public Map<String, Object> deleteDrafts(@RequestBody List<String> ids) {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = draftService.deleteDrafts(ids);
            result.put("success", true);
            result.put("data", count);
            result.put("message", "成功删除 " + count + " 个草稿");
        } catch (Exception e) {
            log.error("批量删除草稿失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }
}
