package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiSessionGroup;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiSessionGroupMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.service.SysAiSessionGroupService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会话分组 Service 实现
 *
 * <p>分组按归属人隔离：写入一律以当前登录用户为归属，读改删只认归属人本身，
 * 管理员可查看全部分组但对他人分组只读。</p>
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class SysAiSessionGroupServiceImpl implements SysAiSessionGroupService {

    private final SysAiSessionGroupMapper sessionGroupMapper;
    private final SysAiSessionMapper sessionMapper;

    @Override
    public List<SysAiSessionGroup> list() {
        LambdaQueryWrapper<SysAiSessionGroup> wrapper = scoped(new LambdaQueryWrapper<>());
        wrapper.orderByAsc(SysAiSessionGroup::getSortOrder);
        return sessionGroupMapper.selectList(wrapper);
    }

    @Override
    public SysAiSessionGroup create(String name) {
        SysAiSessionGroup group = new SysAiSessionGroup();
        group.setName(name);
        group.setUserId(AiUserContext.requireUserId());
        group.setSortOrder(Math.toIntExact(sessionGroupMapper.selectCount(scoped(new LambdaQueryWrapper<>()))));
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());
        sessionGroupMapper.insert(group);
        return group;
    }

    @Override
    public SysAiSessionGroup update(Long id, String name) {
        SysAiSessionGroup group = requireWritable(id);
        group.setName(name);
        group.setUpdateTime(LocalDateTime.now());
        sessionGroupMapper.updateById(group);
        return group;
    }

    @Override
    public void delete(Long id) {
        requireDeletable(id);
        detachSessions(id);
        sessionGroupMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 去重，保持传入顺序
        Set<Long> unique = new LinkedHashSet<>(ids);
        for (Long id : unique) {
            requireDeletable(id);
        }
        for (Long id : unique) {
            detachSessions(id);
            sessionGroupMapper.deleteById(id);
        }
    }

    /**
     * 删除分组前，把引用该分组的未归档会话移回「未分组」（module_code 置空），
     * 与界面提示「该分组下的会话将自动归入任务列表」保持一致。
     *
     * @param id 分组标识
     */
    private void detachSessions(Long id) {
        String groupRef = String.valueOf(id);
        LambdaUpdateWrapper<SysAiSession> update = new LambdaUpdateWrapper<>();
        update.eq(SysAiSession::getSysAiModuleCode, groupRef)
                .eq(SysAiSession::getIsArchived, false)
                .set(SysAiSession::getSysAiModuleCode, null);
        if (true) {
            update.eq(SysAiSession::getSysUserId, AiUserContext.requireUserId());
        }
        sessionMapper.update(null, update);
    }

    @Override
    public void reorder(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            SysAiSessionGroup group = requireWritable(ids.get(i));
            group.setSortOrder(i);
            group.setUpdateTime(LocalDateTime.now());
            sessionGroupMapper.updateById(group);
        }
    }

    /**
     * 追加归属人过滤：管理员不限定，其余用户只作用于自己的分组。
     *
     * @param wrapper 查询条件
     * @return 同一个查询条件对象
     */
    private LambdaQueryWrapper<SysAiSessionGroup> scoped(LambdaQueryWrapper<SysAiSessionGroup> wrapper) {
        if (true) {
            wrapper.eq(SysAiSessionGroup::getUserId, AiUserContext.requireUserId());
        }
        return wrapper;
    }

    /**
     * 写出口归属校验：分组必须存在且归属当前用户，管理员对他人分组只读。
     *
     * @param id 分组标识
     * @return 校验通过的分组
     */
    private SysAiSessionGroup requireWritable(Long id) {
        SysAiSessionGroup group = sessionGroupMapper.selectById(id);
        if (group == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "分组不存在");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(group.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改");
        }
        return group;
    }

    /**
     * 删除出口归属校验：分组必须存在；只读账号禁止；归属本人可删；
     * 管理员在分组管理页可删除任意归属的分组（系统级管理动作）。
     *
     * @param id 分组标识
     * @return 校验通过的分组
     */
    private SysAiSessionGroup requireDeletable(Long id) {
        SysAiSessionGroup group = sessionGroupMapper.selectById(id);
        if (group == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "分组不存在");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许删除");
        }
        boolean owner = AiUserContext.requireUserId().equals(group.getUserId());
        if (!owner && !AiUserContext.isAdmin()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "不可删除他人的分组");
        }
        return group;
    }
}
