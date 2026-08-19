package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiGroup;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import com.chua.starter.ai.support.entity.SysAiModule;
import com.chua.starter.ai.support.mapper.SysAiGroupMapper;
import com.chua.starter.ai.support.mapper.SysAiGroupModelMapper;
import com.chua.starter.ai.support.mapper.AiModuleMapper;
import com.chua.starter.ai.support.service.SysAiGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 模型分组服务实现。
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiGroupServiceImpl implements SysAiGroupService {

    /** SYSAI分组mapper */
    private final SysAiGroupMapper sysAiGroupMapper;
    /** SYSAI分组模型mapper */
    private final SysAiGroupModelMapper sysAiGroupModelMapper;
    /** AImodulemapper */
    private final AiModuleMapper aiModuleMapper;

    @Override
    public List<SysAiGroup> list() {
        return sysAiGroupMapper.selectList(
                Wrappers.lambdaQuery(SysAiGroup.class)
                        .eq(SysAiGroup::getSysAiGroupDelete, 0)
                        .orderByAsc(SysAiGroup::getSysAiGroupSort)
                        .orderByDesc(SysAiGroup::getSysAiGroupId)
        );
    }

    @Override
    public SysAiGroup save(SysAiGroup group) {
        if (group.getSysAiGroupSort() == null) {
            group.setSysAiGroupSort(0);
        }
        if (group.getSysAiGroupStatus() == null) {
            group.setSysAiGroupStatus(1);
        }
        if (group.getSysAiGroupDelete() == null) {
            group.setSysAiGroupDelete(0);
        }
        sysAiGroupMapper.insert(group);
        return group;
    }

    @Override
    public boolean update(SysAiGroup group) {
        return sysAiGroupMapper.updateById(group) > 0;
    }

    @Override
    public boolean deleteById(Integer groupId) {
        if (groupId == null) {
            return false;
        }
        SysAiGroup entity = new SysAiGroup();
        entity.setSysAiGroupId(groupId);
        entity.setSysAiGroupDelete(1);
        return sysAiGroupMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addModels(Integer groupId, List<Integer> moduleIds) {
        if (groupId == null || moduleIds == null || moduleIds.isEmpty()) {
            return;
        }
        for (Integer moduleId : moduleIds) {
            if (moduleId == null) {
                continue;
            }
            // 避免重复插入
            Long exists = sysAiGroupModelMapper.selectCount(
                    Wrappers.lambdaQuery(SysAiGroupModel.class)
                            .eq(SysAiGroupModel::getSysAiGroupId, groupId)
                            .eq(SysAiGroupModel::getSysAiModuleId, moduleId)
            );
            if (exists == 0) {
                SysAiGroupModel gm = new SysAiGroupModel();
                gm.setSysAiGroupId(groupId);
                gm.setSysAiModuleId(moduleId);
                sysAiGroupModelMapper.insert(gm);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeModels(Integer groupId, List<Integer> moduleIds) {
        if (groupId == null || moduleIds == null || moduleIds.isEmpty()) {
            return;
        }
        for (Integer moduleId : moduleIds) {
            sysAiGroupModelMapper.delete(
                    Wrappers.lambdaQuery(SysAiGroupModel.class)
                            .eq(SysAiGroupModel::getSysAiGroupId, groupId)
                            .eq(SysAiGroupModel::getSysAiModuleId, moduleId)
            );
        }
    }

    @Override
    public List<SysAiModule> getGroupModels(Integer groupId) {
        List<Integer> modelIds = getGroupModelIds(groupId);
        if (modelIds.isEmpty()) {
            return Collections.emptyList();
        }
        return aiModuleMapper.selectBatchIds(modelIds);
    }

    @Override
    public List<Integer> getGroupModelIds(Integer groupId) {
        if (groupId == null) {
            return Collections.emptyList();
        }
        List<SysAiGroupModel> list = sysAiGroupModelMapper.selectList(
                Wrappers.lambdaQuery(SysAiGroupModel.class)
                        .eq(SysAiGroupModel::getSysAiGroupId, groupId)
        );
        return list.stream()
                .map(SysAiGroupModel::getSysAiModuleId)
                .collect(Collectors.toList());
    }

    @Override
    public List<SysAiGroup> getModuleGroups(Integer moduleId) {
        if (moduleId == null) {
            return Collections.emptyList();
        }
        List<SysAiGroupModel> gmList = sysAiGroupModelMapper.selectList(
                Wrappers.lambdaQuery(SysAiGroupModel.class)
                        .eq(SysAiGroupModel::getSysAiModuleId, moduleId)
        );
        List<Integer> groupIds = gmList.stream()
                .map(SysAiGroupModel::getSysAiGroupId)
                .distinct()
                .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysAiGroupMapper.selectBatchIds(groupIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceModels(Integer groupId, List<Integer> moduleIds) {
        if (groupId == null) {
            return;
        }
        // 删除旧关联
        sysAiGroupModelMapper.delete(
                Wrappers.lambdaQuery(SysAiGroupModel.class)
                        .eq(SysAiGroupModel::getSysAiGroupId, groupId)
        );
        // 插入新关联
        if (moduleIds != null && !moduleIds.isEmpty()) {
            addModels(groupId, moduleIds);
        }
    }
}
