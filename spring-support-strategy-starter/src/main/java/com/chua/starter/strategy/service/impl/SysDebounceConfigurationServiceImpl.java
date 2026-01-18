package com.chua.starter.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.strategy.entity.SysDebounceConfiguration;
import com.chua.starter.strategy.mapper.SysDebounceConfigurationMapper;
import com.chua.starter.strategy.service.SysDebounceConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防抖配置服务实现
 * 
 * 支持数据库配置和内存缓存同步
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Service
public class SysDebounceConfigurationServiceImpl 
        extends ServiceImpl<SysDebounceConfigurationMapper, SysDebounceConfiguration>
        implements SysDebounceConfigurationService {

    /**
     * 内存缓存：路径 -> 防抖配置
     */
    private final Map<String, SysDebounceConfiguration> debounceConfigCache = new ConcurrentHashMap<>();

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
            int count = refreshDebounceConfigurations();
            log.info("防抖配置初始化完成，加载 {} 个配置到内存", count);
        } catch (Exception e) {
            log.error("防抖配置初始化失败", e);
        }
    }

    @Override
    public IPage<SysDebounceConfiguration> pageForDebounce(Page<SysDebounceConfiguration> page,
                                                             SysDebounceConfiguration sysDebounceConfiguration) {
        LambdaQueryWrapper<SysDebounceConfiguration> queryWrapper = new LambdaQueryWrapper<>();
        
        // 名称模糊查询
        if (StringUtils.hasText(sysDebounceConfiguration.getSysDebounceName())) {
            queryWrapper.like(SysDebounceConfiguration::getSysDebounceName,
                    sysDebounceConfiguration.getSysDebounceName());
        }
        
        // 路径模糊查询
        if (StringUtils.hasText(sysDebounceConfiguration.getSysDebouncePath())) {
            queryWrapper.like(SysDebounceConfiguration::getSysDebouncePath,
                    sysDebounceConfiguration.getSysDebouncePath());
        }
        
        // 状态精确查询
        if (sysDebounceConfiguration.getSysDebounceStatus() != null) {
            queryWrapper.eq(SysDebounceConfiguration::getSysDebounceStatus,
                    sysDebounceConfiguration.getSysDebounceStatus());
        }
        
        // 按排序值和ID排序
        queryWrapper.orderByAsc(SysDebounceConfiguration::getSysDebounceSort)
                   .orderByDesc(SysDebounceConfiguration::getSysDebounceId);
        
        return page(page, queryWrapper);
    }

    @Override
    public List<SysDebounceConfiguration> listEnabledForDebounce() {
        LambdaQueryWrapper<SysDebounceConfiguration> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDebounceConfiguration::getSysDebounceStatus, 1)
                   .orderByAsc(SysDebounceConfiguration::getSysDebounceSort)
                   .orderByDesc(SysDebounceConfiguration::getSysDebounceId);
        
        return list(queryWrapper);
    }

    @Override
    public int refreshDebounceConfigurations() {
        try {
            // 清空缓存
            debounceConfigCache.clear();
            
            // 从数据库加载所有启用的配置
            List<SysDebounceConfiguration> configurations = listEnabledForDebounce();
            
            // 加载到内存缓存
            for (SysDebounceConfiguration config : configurations) {
                debounceConfigCache.put(config.getSysDebouncePath(), config);
                log.debug("加载防抖配置到内存: {} -> {}", config.getSysDebouncePath(), config.getSysDebounceName());
            }
            
            log.info("防抖配置刷新完成，共加载 {} 个配置", configurations.size());
            return configurations.size();
        } catch (Exception e) {
            log.error("刷新防抖配置失败", e);
            throw new RuntimeException("刷新防抖配置失败", e);
        }
    }

    @Override
    public SysDebounceConfiguration matchDebounceConfiguration(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        
        // 优先精确匹配
        if (debounceConfigCache.containsKey(path)) {
            return debounceConfigCache.get(path);
        }
        
        // Ant风格模式匹配，按排序值优先级匹配
        return debounceConfigCache.values().stream()
                .filter(config -> pathMatcher.match(config.getSysDebouncePath(), path))
                .min(Comparator.comparing(SysDebounceConfiguration::getSysDebounceSort))
                .orElse(null);
    }

    @Override
    public boolean save(SysDebounceConfiguration entity) {
        boolean result = super.save(entity);
        if (result && entity.getSysDebounceStatus() == 1) {
            // 保存成功且状态为启用，同步到内存
            debounceConfigCache.put(entity.getSysDebouncePath(), entity);
            log.info("防抖配置已保存并同步到内存: {}", entity.getSysDebounceName());
        }
        return result;
    }

    @Override
    public boolean updateById(SysDebounceConfiguration entity) {
        boolean result = super.updateById(entity);
        if (result) {
            // 更新成功，刷新内存缓存
            if (entity.getSysDebounceStatus() == 1) {
                debounceConfigCache.put(entity.getSysDebouncePath(), entity);
                log.info("防抖配置已更新并同步到内存: {}", entity.getSysDebounceName());
            } else {
                debounceConfigCache.remove(entity.getSysDebouncePath());
                log.info("防抖配置已禁用并从内存移除: {}", entity.getSysDebounceName());
            }
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        SysDebounceConfiguration config = getById(id);
        boolean result = super.removeById(id);
        if (result && config != null) {
            // 删除成功，从内存移除
            debounceConfigCache.remove(config.getSysDebouncePath());
            log.info("防抖配置已删除并从内存移除: {}", config.getSysDebounceName());
        }
        return result;
    }
}
