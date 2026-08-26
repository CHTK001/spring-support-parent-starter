package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerGroup;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerGroupMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.service.ServerGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 服务器分组服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
@RequiredArgsConstructor
public class ServerGroupServiceImpl implements ServerGroupService {

    /** 分组 Mapper */
    private final MsServerGroupMapper groupMapper;
    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsServerGroup> create(MsServerGroup group) {
        if (group.getMsServerGroupName() == null || group.getMsServerGroupName().isEmpty()) {
            return ReturnResult.error("分组名称不能为空");
        }
        if (group.getMsServerGroupParentId() == null) {
            group.setMsServerGroupParentId(0L);
        }
        if (group.getMsServerGroupSort() == null) {
            group.setMsServerGroupSort(0);
        }
        groupMapper.insert(group);
        return ReturnResult.ok(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsServerGroup> update(Long id, MsServerGroup group) {
        MsServerGroup exists = groupMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("分组不存在: " + id);
        }
        if (id.equals(group.getMsServerGroupParentId())) {
            return ReturnResult.error("父分组不能为自身");
        }
        group.setId(id);
        groupMapper.updateById(group);
        return ReturnResult.ok(groupMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
}
