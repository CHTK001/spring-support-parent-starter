package com.chua.starter.task.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SystemTask;
import com.chua.starter.task.support.service.SystemTaskService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.chua.starter.common.support.configuration.CacheConfiguration.DEFAULT_CACHE_MANAGER;

/**
 * @author CH
 */
@Service
public class SystemTaskServiceImpl extends ServiceImpl<SystemTaskMapper, SystemTask> implements SystemTaskService {

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public int deleteWithId(SystemTask task) {
        return baseMapper.deleteById(task);
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public int updateWithId(SystemTask task) {
        return baseMapper.updateById(task);
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public boolean save(SystemTask task) {
        return 1 == baseMapper.insert(task);
    }

    @Override
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public IPage<SystemTask> withPage(Page<SystemTask> page) {
        return baseMapper.selectPage(page, Wrappers.lambdaQuery());
    }

}
