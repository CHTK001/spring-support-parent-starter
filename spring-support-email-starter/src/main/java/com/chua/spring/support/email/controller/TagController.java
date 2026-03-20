package com.chua.spring.support.email.controller;

import com.chua.spring.support.email.entity.AccountTag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 标签管理控制器
 * 
 * @author CH
 */
@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
public class TagController {

    // 模拟数据存储
    private final Map<String, AccountTag> tags = new HashMap<>();

    /**
     * 获取标签列表
     */
    @GetMapping("/list")
    public Map<String, Object> getTagList() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", new ArrayList<>(tags.values()));
        return result;
    }

    /**
     * 创建标签
     */
    @PostMapping("/create")
    public Map<String, Object> createTag(@RequestBody AccountTag tag) {
        tag.setId(UUID.randomUUID().toString());
        tags.put(tag.getId(), tag);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "标签创建成功");
        result.put("data", tag);
        return result;
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteTag(@PathVariable String id) {
        tags.remove(id);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "标签删除成功");
        return result;
    }
}
