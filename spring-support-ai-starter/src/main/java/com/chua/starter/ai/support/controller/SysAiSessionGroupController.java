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
 * @since 4.0.0.51
 */
@RestController
@RequestMapping("/v2/session-groups")
@RequiredArgsConstructor
public class SysAiSessionGroupController {

    private final SysAiSessionGroupService sessionGroupService;

    /**
     * 查询所有分组
     * @return 结果列表，无数据时为空列表
     */
    @GetMapping
    public List<SysAiSessionGroup> list() {
        return sessionGroupService.list();
    }

    /**
     * 创建分组
     * @param body 请求体，不允许为 null
     * @return SysAi会话分组 对象
     */
    @PostMapping
    public SysAiSessionGroup create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return sessionGroupService.create(name);
    }

    /**
     * 更新分组
     * @param id ID，不允许为 null
     * @param body 请求体，不允许为 null
     * @return SysAi会话分组 对象
     */
    @PutMapping("/{id}")
    public SysAiSessionGroup update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        return sessionGroupService.update(id, name);
    }

    /**
     * 删除分组
     * @param id ID，不允许为 null
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        sessionGroupService.delete(id);
    }

    /**
     * 批量删除分组
     * @param body 请求体，ids 为待删除分组 ID 列表
     */
    @PostMapping("/batch-delete")
    public void deleteBatch(@RequestBody Map<String, List<Long>> body) {
        sessionGroupService.deleteBatch(body == null ? null : body.get("ids"));
    }

    /**
     * 排序
     * @param body 请求体，不允许为 null
     */
    @PostMapping("/reorder")
    public void reorder(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("groupIds");
        sessionGroupService.reorder(ids);
    }
}
