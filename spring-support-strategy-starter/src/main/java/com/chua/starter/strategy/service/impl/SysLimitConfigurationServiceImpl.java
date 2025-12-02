package com.chua.starter.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.strategy.entity.SysLimitConfiguration;
import com.chua.starter.strategy.mapper.SysLimitConfigurationMapper;
import com.chua.starter.strategy.service.SysLimitConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 限流配置服务实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLimitConfigurationServiceImpl extends ServiceImpl<SysLimitConfigurationMapper, SysLimitConfiguration>
        implements SysLimitConfigurationService {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public IPage<SysLimitConfiguration> pageForStrategy(IPage<SysLimitConfiguration> page, SysLimitConfiguration entity) {
        LambdaQueryWrapper<SysLimitConfiguration> wrapper = new LambdaQueryWrapper<>();

        // 按规则名称模糊查询
        if (StringUtils.isNotBlank(entity.getSysLimitName())) {
            wrapper.like(SysLimitConfiguration::getSysLimitName, entity.getSysLimitName());
        }

        // 按接口路径模糊查询
        if (StringUtils.isNotBlank(entity.getSysLimitPath())) {
            wrapper.like(SysLimitConfiguration::getSysLimitPath, entity.getSysLimitPath());
        }

        // 按状态查询
        if (entity.getSysLimitStatus() != null) {
            wrapper.eq(SysLimitConfiguration::getSysLimitStatus, entity.getSysLimitStatus());
        }

        // 按排序值排序
        wrapper.orderByAsc(SysLimitConfiguration::getSysLimitSort);

        return page(page, wrapper);
    }

    @Override
    public List<SysLimitConfiguration> listEnabledConfigurations() {
        LambdaQueryWrapper<SysLimitConfiguration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLimitConfiguration::getSysLimitStatus, 1);
        wrapper.orderByAsc(SysLimitConfiguration::getSysLimitSort);
        return list(wrapper);
    }

    @Override
    public SysLimitConfiguration getByPath(String path) {
        // 获取所有启用的配置
        List<SysLimitConfiguration> configs = listEnabledConfigurations();

        // 按排序值查找匹配的配置
        for (SysLimitConfiguration config : configs) {
            if (pathMatcher.match(config.getSysLimitPath(), path)) {
                log.debug("路径 {} 匹配限流规则: {}", path, config.getSysLimitName());
                return config;
            }
        }

        return null;
    }

    @Override
    public void refreshRateLimiters() {
        log.info("开始刷新限流配置到 Resilience4j...");
        List<SysLimitConfiguration> configs = listEnabledConfigurations();

        for (SysLimitConfiguration config : configs) {
            try {
                // TODO: 调用 Resilience4j 动态更新限流器配置
                log.debug("刷新限流规则: {} -> {}", config.getSysLimitName(), config.getSysLimitPath());
            } catch (Exception e) {
                log.error("刷新限流规则失败: {}", config.getSysLimitName(), e);
            }
        }

        log.info("限流配置刷新完成，共 {} 条规则", configs.size());
    }
}
