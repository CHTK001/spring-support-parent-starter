package com.chua.starter.task.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.task.support.execute.TaskExecutor;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SystemTask;
import com.chua.starter.task.support.service.SystemTaskService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.chua.starter.common.support.configuration.CacheConfiguration.DEFAULT_CACHE_MANAGER;

/**
 * @author CH
 */
@Service
public class SystemTaskServiceImpl extends ServiceImpl<SystemTaskMapper, SystemTask> implements SystemTaskService {


    @Resource
    private TaskExecutor taskExecutor;

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public int deleteWithId(SystemTask task) {
        int i = baseMapper.deleteById(task);
        if (i != 0) {
            taskExecutor.unregister(task);
        }
        return i;
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public int updateWithId(SystemTask task) {
        return baseMapper.updateById(task);
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public boolean save(SystemTask task) {
        boolean b = 1 == baseMapper.insert(task);
        if (b) {
            taskExecutor.register(task);
        }
        return b;
    }

    @Override
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public IPage<SystemTask> withPage(Page<SystemTask> page) {
        return baseMapper.selectPage(page, Wrappers.lambdaQuery());
    }

}
