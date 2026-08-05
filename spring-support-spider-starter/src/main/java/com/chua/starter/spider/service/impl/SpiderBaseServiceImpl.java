package com.chua.starter.spider.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.entity.SpiderBase;
import com.chua.starter.spider.service.SpiderBaseService;

import java.time.LocalDateTime;

/**
 * 爬虫模块服务公共实现类。
 *
 * <p>基于 {@link ServiceImpl} 提供标准 CRUD，并实现时间戳自动维护。</p>
 *
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 * @author CH
 * @since 4.0.0.42
 */
public abstract class SpiderBaseServiceImpl<M extends BaseMapper<T>, T extends SpiderBase>
        extends ServiceImpl<M, T> implements SpiderBaseService<T> {

    @Override
    public boolean saveWithTime(T entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return save(entity);
    }

    @Override
    public boolean updateWithTime(T entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return updateById(entity);
    }
}