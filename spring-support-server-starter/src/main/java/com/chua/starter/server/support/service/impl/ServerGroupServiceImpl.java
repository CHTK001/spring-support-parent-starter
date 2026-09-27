package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerGroup;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerGroupMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.vo.ServerGroupNodeVO;
import com.chua.starter.server.support.service.ServerGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务器分组服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
@RequiredArgsConstructor
public class ServerGroupServiceImpl implements ServerGroupService {

    /**
     * 分组 映射器
    */
    private final MsServerGroupMapper groupMapper;
    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建。
     * @param group 群体
     * @return 创建的结果
     */
    public ReturnResult<MsServerGroup> create(MsServerGroup group) {
        if (group.getMsServerGroupName() == null || group.getMsServerGroupName().isEmpty()) {
            return ReturnResult.error("分组名称不能为空");
        }
        if (group.getMsServerGroupParentId() == null) {
            group.setMsServerGroupParentId(0L);
        }
        if (group.getMsServerGroupParentId() != 0L
                && groupMapper.selectById(group.getMsServerGroupParentId()) == null) {
            return ReturnResult.error("父分组不存在: " + group.getMsServerGroupParentId());
        }
        if (group.getMsServerGroupSort() == null) {
            group.setMsServerGroupSort(0);
        }
        groupMapper.insert(group);
        return ReturnResult.ok(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新。
     * @param id 标识
     * @param group 群体
     * @return 更新的结果
     */
    public ReturnResult<MsServerGroup> update(Long id, MsServerGroup group) {
        MsServerGroup exists = groupMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("分组不存在: " + id);
        }
        Long parentId = group.getMsServerGroupParentId();
        if (id.equals(parentId)) {
            return ReturnResult.error("父分组不能为自身");
        }
        if (parentId != null && parentId != 0L) {
            if (groupMapper.selectById(parentId) == null) {
                return ReturnResult.error("父分组不存在: " + parentId);
            }
            if (isDescendant(id, parentId)) {
                return ReturnResult.error("父分组不能是自己的子孙分组");
            }
        }
        group.setId(id);
        groupMapper.updateById(group);
        return ReturnResult.ok(groupMapper.selectById(id));
    }

    /**
     * 判断候选父节点是否为自身的子孙（沿父链上溯，遇到自身即为环）
     *
     * @param id       被移动的分组主键
     * @param parentId 候选父分组主键
     * @return 候选父节点落在自身子树内时返回 true
     */
    private boolean isDescendant(Long id, Long parentId) {
        Map<Long, Long> parentOf = new HashMap<>();
        for (MsServerGroup g : groupMapper.selectList(null)) {
            parentOf.put(g.getId(), g.getMsServerGroupParentId());
        }
        Set<Long> walked = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L && walked.add(cursor)) {
            if (cursor.equals(id)) {
                return true;
            }
            cursor = parentOf.get(cursor);
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Void> delete(Long id) {
        Long childCount = groupMapper.selectCount(
                new LambdaQueryWrapper<MsServerGroup>().eq(MsServerGroup::getMsServerGroupParentId, id));
        if (childCount != null && childCount > 0) {
            return ReturnResult.error("存在子分组，禁止删除");
        }
        Long hostCount = hostMapper.selectCount(
                new LambdaQueryWrapper<MsServerHost>().eq(MsServerHost::getMsServerGroupId, id));
        if (hostCount != null && hostCount > 0) {
            return ReturnResult.error("分组下存在主机，禁止删除");
        }
        groupMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<MsServerGroup>> list() {
        List<MsServerGroup> groups = groupMapper.selectList(
                new LambdaQueryWrapper<MsServerGroup>().orderByAsc(MsServerGroup::getMsServerGroupSort));
        return ReturnResult.list(groups);
    }

    @Override
    public ReturnResult<List<ServerGroupNodeVO>> tree() {
        List<MsServerGroup> all = groupMapper.selectList(
                new LambdaQueryWrapper<MsServerGroup>().orderByAsc(MsServerGroup::getMsServerGroupSort));
        Map<Long, List<MsServerGroup>> childrenMap = new HashMap<>();
        for (MsServerGroup g : all) {
            Long pid = g.getMsServerGroupParentId();
            if (pid != null && pid != 0L) {
                childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(g);
            }
        }
        List<ServerGroupNodeVO> roots = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (MsServerGroup g : all) {
            Long pid = g.getMsServerGroupParentId();
            if (pid == null || pid == 0L) {
                roots.add(toNode(g, childrenMap, visited));
            }
        }
        return ReturnResult.list(roots);
    }

    /**
     * 递归组装节点，visited 保证脏数据成环时不会无限下钻
     *
     * @param group       当前分组
     * @param childrenMap 父键到子分组的索引
     * @param visited     本轮已展开的分组主键
     * @return 树节点，叶子节点 children 为空列表
     */
    private ServerGroupNodeVO toNode(MsServerGroup group, Map<Long, List<MsServerGroup>> childrenMap, Set<Long> visited) {
        List<ServerGroupNodeVO> children = new ArrayList<>();
        if (visited.add(group.getId())) {
            for (MsServerGroup sub : childrenMap.getOrDefault(group.getId(), List.of())) {
                children.add(toNode(sub, childrenMap, visited));
            }
        }
        return new ServerGroupNodeVO(group.getId(), group.getMsServerGroupName(),
                group.getMsServerGroupParentId(), group.getMsServerGroupSort(),
                group.getMsServerGroupRemark(), group.getMsServerGroupColor(), children);
    }
}
