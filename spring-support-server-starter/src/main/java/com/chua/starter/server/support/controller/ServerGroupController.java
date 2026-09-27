package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerGroup;
import com.chua.starter.server.support.model.vo.ServerGroupNodeVO;
import com.chua.starter.server.support.service.ServerGroupService;
import io.swagger.v3.oas.annotations.Operation;
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
 * 服务器分组接口
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-分组", description = "主机分组管理")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/group")
@RequiredArgsConstructor
public class ServerGroupController {

    /**
     * 分组服务
    */
    private final ServerGroupService serverGroupService;

    /**
     * 新增分组
     *
     * @param group 分组
     * @return 结果
     */
    @Operation(summary = "新增分组")
    @PostMapping
    public ReturnResult<MsServerGroup> create(@RequestBody MsServerGroup group) {
        return serverGroupService.create(group);
    }

    /**
     * 更新分组
     *
     * @param id    主键
     * @param group 分组
     * @return 结果
     */
    @Operation(summary = "更新分组")
    @PutMapping("/{id}")
    public ReturnResult<MsServerGroup> update(@PathVariable Long id, @RequestBody MsServerGroup group) {
        return serverGroupService.update(id, group);
    }

    /**
     * 删除分组
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return serverGroupService.delete(id);
    }

    /**
     * 分组列表（平铺，前端组树）
     *
     * @return 列表
     */
    @Operation(summary = "分组列表")
    @GetMapping
    public ReturnResult<List<MsServerGroup>> list() {
        return serverGroupService.list();
    }

    /**
     * 分组树（按 msServerGroupParentId 组装，根节点 parentId 为 null 或 0）
     *
     * @return 树结构列表
     */
    @Operation(summary = "分组树")
    @GetMapping("/tree")
    public ReturnResult<List<ServerGroupNodeVO>> tree() {
        return serverGroupService.tree();
    }
}
