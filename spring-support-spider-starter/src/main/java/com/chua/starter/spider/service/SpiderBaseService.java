package com.chua.starter.spider.service;

import com.chua.starter.spider.entity.SpiderBase;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * 爬虫模块服务公共基类。
 *
 * <p>在写入（新增/修改）时负责维护公共的创建时间与更新时间字段，
 * 统一时间戳赋值逻辑，避免每处 Service 重复处理。</p>
 *
 * @param <T> 实体类型
 * @author CH
 * @since 4.0.0.42
 */
public interface SpiderBaseService<T extends SpiderBase> extends IService<T> {

    /**
     * 新增记录，自动填充创建时间与更新时间。
     *
     * @param entity 待新增实体
     * @return 是否成功
     */
    boolean saveWithTime(T entity);

    /**
     * 更新记录，自动刷新更新时间。
     *
     * @param entity 待更新实体
     * @return 是否成功
     */
    boolean updateWithTime(T entity);
}