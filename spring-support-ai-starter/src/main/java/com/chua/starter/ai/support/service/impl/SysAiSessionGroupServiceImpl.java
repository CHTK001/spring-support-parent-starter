package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiSessionGroup;
import com.chua.starter.ai.support.mapper.SysAiSessionGroupMapper;
import com.chua.starter.ai.support.service.SysAiSessionGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话分组 Service 实现
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class SysAiSessionGroupServiceImpl implements SysAiSessionGroupService {

    private final SysAiSessionGroupMapper sessionGroupMapper;

    @Override
    public List<SysAiSessionGroup> list() {
        LambdaQueryWrapper<SysAiSessionGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysAiSessionGroup::getSortOrder);
        return sessionGroupMapper.selectList(wrapper);
    }

    @Override
    public SysAiSessionGroup create(String name) {
        SysAiSessionGroup group = new SysAiSessionGroup();
        group.setName(name);
        group.setSortOrder(list().size());
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());
        sessionGroupMapper.insert(group);
        return group;
    }

    @Override
    public SysAiSessionGroup update(Long id, String name) {
        SysAiSessionGroup group = sessionGroupMapper.selectById(id);
        if (group != null) {
            group.setName(name);
            group.setUpdateTime(LocalDateTime.now());
            sessionGroupMapper.updateById(group);
        }
        return group;
    }

    @Override
    public void delete(Long id) {
        sessionGroupMapper.deleteById(id);
    }

    @Override
    public void reorder(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            SysAiSessionGroup group = sessionGroupMapper.selectById(ids.get(i));
            if (group != null) {
                group.setSortOrder(i);
                group.setUpdateTime(LocalDateTime.now());
                sessionGroupMapper.updateById(group);
            }
        }
    }
}
