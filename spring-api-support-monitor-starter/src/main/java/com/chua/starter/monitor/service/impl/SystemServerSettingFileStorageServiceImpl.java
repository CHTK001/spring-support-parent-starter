package com.chua.starter.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.entity.SystemServerSettingFileStorage;
import com.chua.starter.monitor.mapper.SystemServerSettingFileStorageMapper;
import com.chua.starter.monitor.service.SystemServerSettingFileStorageService;
import com.chua.starter.monitor.service.SystemServerSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerSettingFileStorageServiceImpl implements SystemServerSettingFileStorageService {

    private final SystemServerSettingFileStorageMapper mapper;
    private final SystemServerSettingService systemServerSettingService;

    @Override
    public List<SystemServerSettingFileStorage> listByServerId(Integer serverId) {
        return mapper.selectByServerId(serverId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<SystemServerSettingFileStorage> saveOrUpdate(SystemServerSettingFileStorage config) {
        try {
            if (config.getSystemServerSettingFileStorageId() == null) {
                mapper.insert(config);
            } else {
                mapper.updateById(config);
            }
            systemServerSettingService.applyConfigToRunningServer(config.getFileStorageServerId());
            return ReturnResult.ok(config);
        } catch (Exception e) {
            log.error("保存文件存储配置失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteByServerId(Integer serverId) {
        try {
            mapper.delete(new QueryWrapper<SystemServerSettingFileStorage>().eq("file_storage_server_id", serverId));
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("删除文件存储配置失败", e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }
}

