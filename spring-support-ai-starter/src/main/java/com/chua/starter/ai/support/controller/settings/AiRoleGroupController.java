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

    @Operation(summary = "查询所有角色分组")
    @GetMapping
    public List<SysAiRoleGroup> list() {
        return roleGroupMapper.selectList(
            new LambdaQueryWrapper<SysAiRoleGroup>()
                .orderByAsc(SysAiRoleGroup::getSort)
        );
    }

    @Operation(summary = "新增角色分组")
    @PostMapping
    public SysAiRoleGroup create(@RequestBody SysAiRoleGroup group) {
        roleGroupMapper.insert(group);
        return group;
    }

    @Operation(summary = "更新角色分组")
    @PutMapping("/{id}")
    public boolean update(@PathVariable Integer id, @RequestBody SysAiRoleGroup group) {
        group.setId(id);
        return roleGroupMapper.updateById(group) > 0;
    }

    @Operation(summary = "删除角色分组")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return roleGroupMapper.deleteById(id) > 0;
    }
}
