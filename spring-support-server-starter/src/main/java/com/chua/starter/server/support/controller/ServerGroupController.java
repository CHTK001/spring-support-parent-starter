package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerGroup;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 分组服务 */
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
    public ReturnResult<List<Map<String, Object>>> tree() {
        ReturnResult<List<MsServerGroup>> result = serverGroupService.list();
        List<MsServerGroup> all = result.getData();
        if (all == null) {
            all = new ArrayList<>();
        }

        // 按 parentId 分组；根节点 parentId 为 null 或 0
        Map<Long, List<MsServerGroup>> childrenMap = all.stream()
                .filter(g -> g.getMsServerGroupParentId() != null && g.getMsServerGroupParentId() != 0L)
                .collect(Collectors.groupingBy(MsServerGroup::getMsServerGroupParentId));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (MsServerGroup g : all) {
            Long pid = g.getMsServerGroupParentId();
            if (pid == null || pid == 0L) {
                tree.add(toNode(g, childrenMap));
            }
        }
        return ReturnResult.ok(tree);
    }

    /**
    * 将实体转换为树节点并递归组装 children
    * @param g 方法入参 g
    * @param childrenMap children映射，不允许为 null
    * @return 结果映射，无数据时为空映射
    */
    private Map<String, Object> toNode(MsServerGroup g, Map<Long, List<MsServerGroup>> childrenMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", g.getId());
        node.put("name", g.getMsServerGroupName());
        node.put("parentId", g.getMsServerGroupParentId());
        node.put("sort", g.getMsServerGroupSort());
        node.put("remark", g.getMsServerGroupRemark());
        node.put("color", g.getMsServerGroupColor());
        List<Map<String, Object>> children = new ArrayList<>();
        List<MsServerGroup> subs = childrenMap.get(g.getId());
        if (subs != null) {
            for (MsServerGroup sub : subs) {
                children.add(toNode(sub, childrenMap));
            }
        }
        node.put("children", children);
        return node;
    }
}
