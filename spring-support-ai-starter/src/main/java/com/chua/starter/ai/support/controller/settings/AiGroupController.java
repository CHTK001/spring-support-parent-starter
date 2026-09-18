package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiGroupDTO;
import com.chua.starter.ai.support.service.settings.AiGroupService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
* 模型分组控制器，提供分组的增删改查。
*
* <p>所有写接口均受 {@code @Permission("admin")} 保护。</p>
*
* @since 2026-08-03
* @author CH
 */
@RestController
@RequestMapping("/v2/groups")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 模型分组")
public class AiGroupController {

    /**
    * 分组服务
    */
    private final AiGroupService groupService;

    /**
    * 查询所有分组。
    *
    * @return 分组 DTO 列表
    */
    @Permission("admin")
    @Operation(summary = "查询所有分组")
    @GetMapping
    public List<AiGroupDTO> list() {
        return groupService.list();
    }

    /**
    * 新建分组。
    *
    * @param group 分组 DTO
    * @return 创建后的分组 DTO
    */
    @Permission("admin")
    @Operation(summary = "新建分组")
    @PostMapping
    public AiGroupDTO create(@RequestBody AiGroupDTO group) {
        return groupService.create(group);
    }

    /**
    * 更新分组。
    *
    * @param groupId 分组主键
    * @param group   分组 DTO
    * @return 更新后的分组 DTO
    * @param ID" 标识"
    */
    @Permission("admin")
    @Operation(summary = "更新分组")
    @PutMapping("/{groupId}")
    public AiGroupDTO update(@Parameter(description = "分组主键 ID") @PathVariable Long groupId,
                             @RequestBody AiGroupDTO group) {
        return groupService.update(groupId, group);
    }

    /**
    * 删除分组（逻辑删除）。
    *
    * @param groupId 分组主键
    * @param ID" 标识"
    * @param ID" 标识"
    * @param ID" ID"
    */
    @Permission("admin")
    @Operation(summary = "删除分组")
    @DeleteMapping("/{groupId}")
    public void delete(@Parameter(description = "分组主键 ID") @PathVariable Long groupId) {
        groupService.delete(groupId);
    }
}
