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

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流配置服务实现
 * 
 * 支持数据库配置和内存缓存同步
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

    /**
     * 内存缓存：路径 -> 限流配置
     */
    private final Map<String, SysLimitConfiguration> limitConfigCache = new ConcurrentHashMap<>();

    /**
     * 路径匹配器
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 应用启动时加载配置到内存
     */
    @PostConstruct
    public void init() {
        try {
            refreshRateLimiters();
            log.info("限流配置初始化完成，加载 {} 个配置到内存", limitConfigCache.size());
        } catch (Exception e) {
            log.error("限流配置初始化失败", e);
        }
    }

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
        if (StringUtils.isBlank(path)) {
            return null;
        }
        
        // 优先精确匹配
        if (limitConfigCache.containsKey(path)) {
            return limitConfigCache.get(path);
        }
        
        // Ant风格模式匹配，按排序值优先级匹配
        return limitConfigCache.values().stream()
                .filter(config -> pathMatcher.match(config.getSysLimitPath(), path))
                .min(Comparator.comparing(SysLimitConfiguration::getSysLimitSort))
                .orElse(null);
    }

    @Override
    public void refreshRateLimiters() {
        try {
            // 清空缓存
            limitConfigCache.clear();
            
            // 从数据库加载所有启用的配置
            List<SysLimitConfiguration> configs = listEnabledConfigurations();
            
            // 加载到内存缓存
            for (SysLimitConfiguration config : configs) {
                limitConfigCache.put(config.getSysLimitPath(), config);
                log.debug("加载限流配置到内存: {} -> {}", config.getSysLimitPath(), config.getSysLimitName());
            }
            
            log.info("限流配置刷新完成，共加载 {} 个配置", configs.size());
        } catch (Exception e) {
            log.error("刷新限流配置失败", e);
            throw new RuntimeException("刷新限流配置失败", e);
        }
    }

    @Override
    public boolean save(SysLimitConfiguration entity) {
        boolean result = super.save(entity);
        if (result && entity.getSysLimitStatus() == 1) {
            // 保存成功且状态为启用，同步到内存
            limitConfigCache.put(entity.getSysLimitPath(), entity);
            log.info("限流配置已保存并同步到内存: {}", entity.getSysLimitName());
        }
        return result;
    }

    @Override
    public boolean updateById(SysLimitConfiguration entity) {
        boolean result = super.updateById(entity);
        if (result) {
            // 更新成功，刷新内存缓存
            if (entity.getSysLimitStatus() == 1) {
                limitConfigCache.put(entity.getSysLimitPath(), entity);
                log.info("限流配置已更新并同步到内存: {}", entity.getSysLimitName());
            } else {
                limitConfigCache.remove(entity.getSysLimitPath());
                log.info("限流配置已禁用并从内存移除: {}", entity.getSysLimitName());
            }
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        SysLimitConfiguration config = getById(id);
        boolean result = super.removeById(id);
        if (result && config != null) {
            // 删除成功，从内存移除
            limitConfigCache.remove(config.getSysLimitPath());
            log.info("限流配置已删除并从内存移除: {}", config.getSysLimitName());
        }
        return result;
    }
}
