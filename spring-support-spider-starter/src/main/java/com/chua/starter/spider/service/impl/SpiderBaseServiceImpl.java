package com.chua.starter.spider.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.spider.service.SpiderBaseService;

/**
 * 爬虫模块服务公共实现类。
 *
 * <p>基于 {@link ServiceImpl} 提供标准 CRUD，公共字段由
 * {@code SpiderMetaObjectHandler} 自动填充。</p>
 *
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 * @since 4.0.0.42
 */
public abstract class SpiderBaseServiceImpl<M extends BaseMapper<T>, T extends SysBase>
        extends ServiceImpl<M, T> implements SpiderBaseService<T> {
}