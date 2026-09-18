package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.entity.SysAiRoleGroup;
import com.chua.starter.ai.support.mapper.SysAiRoleGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色分组控制器
 */
@Tag(name = "AI 设置 - 角色分组")
@RestController
@RequestMapping("/v2/role-groups")
@RequiredArgsConstructor
public class AiRoleGroupController {

    private final SysAiRoleGroupMapper roleGroupMapper;

    /**
     * 列出。
     *
     * @return 结果列表，无数据时为空列表
     */
    @Operation(summary = "查询所有角色分组")
    @GetMapping
    public List<SysAiRoleGroup> list() {
        return roleGroupMapper.selectList(
            new LambdaQueryWrapper<SysAiRoleGroup>()
                .orderByAsc(SysAiRoleGroup::getSort)
        );
    }

    /**
     * 创建。
     *
     * @param group 分组，不允许为 null
     * @return SysAiRole分组 对象
     */
    @Operation(summary = "新增角色分组")
    @PostMapping
    public SysAiRoleGroup create(@RequestBody SysAiRoleGroup group) {
        roleGroupMapper.insert(group);
        return group;
    }

    /**
     * 更新。
     *
     * @param id ID，不允许为 null
     * @param group 分组，不允许为 null
     * @return 是否成功（true 表示成功）
     */
    @Operation(summary = "更新角色分组")
    @PutMapping("/{id}")
    public boolean update(@PathVariable Integer id, @RequestBody SysAiRoleGroup group) {
        group.setId(id);
        return roleGroupMapper.updateById(group) > 0;
    }

    /**
     * 删除。
     *
     * @param id ID，不允许为 null
     * @return 是否成功（true 表示成功）
     */
    @Operation(summary = "删除角色分组")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return roleGroupMapper.deleteById(id) > 0;
    }
}
