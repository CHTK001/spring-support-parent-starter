package com.chua.starter.software.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.software.support.entity.SoftConfig;
import com.chua.starter.software.support.mapper.SoftConfigMapper;
import com.chua.starter.software.support.service.SoftConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 软件配置表 Service 实现
 *
 * @author CH
 * @since 2026/7/25
 */
@Service
@RequiredArgsConstructor
public class SoftConfigServiceImpl extends ServiceImpl<SoftConfigMapper, SoftConfig> implements SoftConfigService {

    /**
     * 事务模板
     */
    private final TransactionTemplate transactionTemplate;

    @Override
    public IPage<SoftConfig> pageFor(com.chua.starter.mybatis.entity.Query<SoftConfig> query, SoftConfig request) {
        Page<SoftConfig> page = query.createPage();
        return baseMapper.selectPageFor(page, request);
    }

    @Override
    public ReturnResult<SoftConfig> saveConfig(SoftConfig config) {
        if (config.getConfigSoftwareId() == null) {
            return ReturnResult.illegal("软件ID不能为空");
        }
        if (config.getConfigKey() == null) {
            return ReturnResult.illegal("配置键不能为空");
        }
        config.setConfigCreateTime(LocalDateTime.now());
        baseMapper.insert(config);
        return ReturnResult.ok(config);
    }

    @Override
    public ReturnResult<SoftConfig> updateConfig(SoftConfig config) {
        if (config.getConfigId() == null) {
            return ReturnResult.illegal("配置ID不能为空");
        }
        SoftConfig exist = getById(config.getConfigId());
        if (exist == null) {
            return ReturnResult.illegal("配置不存在");
        }
        config.setConfigUpdateTime(LocalDateTime.now());
        baseMapper.updateById(config);
        return ReturnResult.ok(config);
    }

    @Override
    public ReturnResult<Boolean> deleteConfig(String configId) {
        if (configId == null) {
            return ReturnResult.illegal("配置ID不能为空");
        }
        SoftConfig exist = getById(configId);
        if (exist == null) {
            return ReturnResult.illegal("配置不存在");
        }
        baseMapper.deleteById(configId);
        return ReturnResult.ok(true);
    }

    @Override
    public SoftConfig getBySoftwareAndKey(String softwareId, String key) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SoftConfig>()
                .eq(SoftConfig::getConfigSoftwareId, softwareId)
                .and(q -> q.eq(SoftConfig::getConfigKey, key))
                .orderByDesc(SoftConfig::getConfigCreateTime)
                .last("LIMIT 1"));
    }

    @Override
    public List<SoftConfig> listBySoftwareId(String softwareId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftConfig>()
                .eq(SoftConfig::getConfigSoftwareId, softwareId)
                .orderByAsc(SoftConfig::getConfigGroup)
                .orderByAsc(SoftConfig::getConfigSort));
    }

    @Override
    public List<SoftConfig> listBySoftwareIdAndUserId(String softwareId, String userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftConfig>()
                .eq(SoftConfig::getConfigSoftwareId, softwareId)
                .and(q -> q.isNull(SoftConfig::getConfigUserId)
                        .or().eq(SoftConfig::getConfigUserId, userId))
                .orderByAsc(SoftConfig::getConfigGroup)
                .orderByAsc(SoftConfig::getConfigSort));
    }

    @Override
    public List<SoftConfig> listBySoftwareIdAndDeviceId(String softwareId, String deviceId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftConfig>()
                .eq(SoftConfig::getConfigSoftwareId, softwareId)
                .and(q -> q.isNull(SoftConfig::getConfigDeviceId)
                        .or().eq(SoftConfig::getConfigDeviceId, deviceId))
                .orderByAsc(SoftConfig::getConfigGroup)
                .orderByAsc(SoftConfig::getConfigSort));
    }

    @Override
    public ReturnResult<Boolean> batchSaveOrUpdate(List<SoftConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return ReturnResult.ok(true);
        }
        LocalDateTime now = LocalDateTime.now();
        for (SoftConfig config : configs) {
            if (StringUtils.isBlank(config.getConfigId())) {
                config.setConfigCreateTime(now);
                baseMapper.insert(config);
            } else {
                config.setConfigUpdateTime(now);
                baseMapper.updateById(config);
            }
        }
        return ReturnResult.ok(true);
    }
}