package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.entity.SysAiSessionGroup;
import com.chua.starter.ai.support.service.SysAiSessionGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话分组 Controller
 *
 * @author CH
 */
@RestController
@RequestMapping("/v2/session-groups")
@RequiredArgsConstructor
public class SysAiSessionGroupController {

    private final SysAiSessionGroupService sessionGroupService;

    /**
    * 查询所有分组
    */
    @GetMapping
    public List<SysAiSessionGroup> list() {
        return sessionGroupService.list();
    }

    /**
    * 创建分组
    */
    @PostMapping
    public SysAiSessionGroup create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return sessionGroupService.create(name);
    }

    /**
    * 更新分组
    */
    @PutMapping("/{id}")
    public SysAiSessionGroup update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        return sessionGroupService.update(id, name);
    }

    /**
    * 删除分组
    */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        sessionGroupService.delete(id);
    }

    /**
    * 排序
    */
    @PostMapping("/reorder")
    public void reorder(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("groupIds");
        sessionGroupService.reorder(ids);
    }
}
