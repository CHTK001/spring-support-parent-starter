package com.chua.starter.task.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.task.support.execute.SystemTaskExecutor;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SysTask;
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
public class SystemTaskServiceImpl extends ServiceImpl<SystemTaskMapper, SysTask> implements SystemTaskService {


    @Resource
    private SystemTaskExecutor systemTaskExecutor;

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public int deleteWithId(SysTask task) {
        int i = baseMapper.deleteById(task);
        if (i != 0) {
            systemTaskExecutor.unregister(task);
        }
        return i;
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public int updateWithId(SysTask task) {
        int i = baseMapper.updateById(task);
        if (i != 0) {
            systemTaskExecutor.update(task);
        }
        return i;
    }

    @Override
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public boolean save(SysTask task) {
        boolean b = 1 == baseMapper.insert(task);
        if (b) {
            systemTaskExecutor.register(task);
        }
        return b;
    }

    @Override
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'task'")
    public IPage<SysTask> withPage(Page<SysTask> page) {
        return baseMapper.selectPage(page, Wrappers.lambdaQuery());
    }

}
