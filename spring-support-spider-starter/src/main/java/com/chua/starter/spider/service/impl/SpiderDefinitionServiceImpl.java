package com.chua.starter.spider.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.mapper.SpiderDefinitionMapper;
import com.chua.starter.spider.service.SpiderDefinitionService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 爬虫定义服务实现。
 *
 * <p>启用列表使用 {@link Cacheable} 缓存，缓存在 Caffeine 中；
 * 新增/更新/删除时通过 {@link CacheEvict} 清理全部缓存项，保证一致性。
 * 调度器回写 {@code last_run_at} 走 {@link #markRunAtOnly}，绕开缓存清理。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@CacheConfig(cacheNames = "spider")
@Service
public class SpiderDefinitionServiceImpl
        extends SpiderBaseServiceImpl<SpiderDefinitionMapper, SpiderDefinition>
        implements SpiderDefinitionService {

    /**
     * 启用列表缓存 Key 常量
     */
    private static final String ENABLED_LIST_KEY = "'definitions:enabled'";

    @Cacheable(key = ENABLED_LIST_KEY)
    @Override
    /** 列表已启用 */
    public List<SpiderDefinition> listEnabled() {
        return list(Wrappers.<SpiderDefinition>lambdaQuery()
                .eq(SpiderDefinition::getEnabled, Integer.valueOf(1)));
    }

    @Override
    /** 标记运行atonly */
    public boolean markRunAtOnly(Long id, LocalDateTime lastRunAt) {
        if (id == null || lastRunAt == null) {
            return false;
        }
        LambdaUpdateWrapper<SpiderDefinition> wrapper = Wrappers.<SpiderDefinition>lambdaUpdate()
                .eq(SpiderDefinition::getId, id)
                .set(SpiderDefinition::getLastRunAt, lastRunAt);
        return baseMapper.update(null, wrapper) > 0;
    }

    @CacheEvict(allEntries = true)
    @Override
    /** 保存 */
    public boolean save(SpiderDefinition entity) {
        return super.save(entity);
    }

    @CacheEvict(allEntries = true)
    @Override
    /** 更新byid */
    public boolean updateById(SpiderDefinition entity) {
        return super.updateById(entity);
    }

    @CacheEvict(allEntries = true)
    @Override
    /** 移除byid */
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
