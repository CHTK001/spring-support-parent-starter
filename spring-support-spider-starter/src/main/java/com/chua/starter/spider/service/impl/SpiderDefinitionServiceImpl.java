package com.chua.starter.spider.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.mapper.SpiderDefinitionMapper;
import com.chua.starter.spider.service.SpiderDefinitionService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/**
 * 爬虫定义服务实现。
 *
 * <p>启用列表使用 {@link Cacheable} 缓存，缓存在 Caffeine 中；
 * 新增/更新/删除时通过 {@link CacheEvict} 清理全部缓存项，保证一致性。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@CacheConfig(cacheNames = "spider")
@Service
public class SpiderDefinitionServiceImpl
        extends SpiderBaseServiceImpl<SpiderDefinitionMapper, SpiderDefinition>
        implements SpiderDefinitionService {

    /**
     * 启用列表缓存 key 常量
     */
    private static final String ENABLED_LIST_KEY = "'definitions:enabled'";

    @Cacheable(key = ENABLED_LIST_KEY)
    @Override
    public List<SpiderDefinition> listEnabled() {
        return list(Wrappers.<SpiderDefinition>lambdaQuery()
                .eq(SpiderDefinition::getEnabled, Integer.valueOf(1)));
    }

    @CacheEvict(allEntries = true)
    @Override
    public boolean save(SpiderDefinition entity) {
        return super.save(entity);
    }

    @CacheEvict(allEntries = true)
    @Override
    public boolean updateById(SpiderDefinition entity) {
        return super.updateById(entity);
    }

    @CacheEvict(allEntries = true)
    @Override
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}