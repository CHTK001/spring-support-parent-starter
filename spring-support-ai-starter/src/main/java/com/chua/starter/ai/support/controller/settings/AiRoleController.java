package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.service.SysAiRoleService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * AI 角色（人设）控制器，提供角色的增删改查。
 *
 * <p>角色是一段预设的系统提示词人物设定，让 AI 以特定身份（如 Java 开发、
 * UI 设计师）应答。写接口受 {@code @Permission("admin")} 保护。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Tag(name = "AI 设置 - 角色")
@RestController
@RequestMapping("/v2/roles")
@RequiredArgsConstructor
public class AiRoleController {

    /**
     * 角色服务
     */
    private final SysAiRoleService roleService;

    /**
     * 查询所有角色。
     *
     * @return 角色列表
     */
    @Operation(summary = "查询所有角色")
    @GetMapping
    public List<SysAiRole> list() {
        return roleService.list();
    }

    /**
     * 查询所有启用角色（供前端下拉选择）。
     *
     * @return 启用角色列表
     */
    @Operation(summary = "查询启用角色")
    @GetMapping("/enabled")
    public List<SysAiRole> listEnabled() {
        return roleService.listEnabled();
    }

    /**
     * 按 ID 查询角色。
     *
     * @param id 角色主键
     * @return 角色实体
     */
    @Operation(summary = "查询单个角色")
    @GetMapping("/{id}")
    public SysAiRole getById(@Parameter(description = "角色主键") @PathVariable Integer id) {
        return roleService.getById(id);
    }

    /**
     * 新增角色。
     *
     * @param role 角色实体
     * @return 保存后的角色
     */
    @Permission("admin")
    @Operation(summary = "新增角色")
    @PostMapping
    public SysAiRole create(@RequestBody SysAiRole role) {
        return roleService.save(role);
    }

    /**
     * 更新角色。
     *
     * @param id   角色主键
     * @param role 角色实体
     * @return 是否成功
     */
    @Permission("admin")
    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public boolean update(@Parameter(description = "角色主键") @PathVariable Integer id,
                          @RequestBody SysAiRole role) {
        role.setId(id);
        return roleService.update(role);
    }

    /**
     * 删除角色（逻辑删除）。
     *
     * @param id 角色主键
     * @return 是否成功
     */
    @Permission("admin")
    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public boolean delete(@Parameter(description = "角色主键") @PathVariable Integer id) {
        return roleService.deleteById(id);
    }
}