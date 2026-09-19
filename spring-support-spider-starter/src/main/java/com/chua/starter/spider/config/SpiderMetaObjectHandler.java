package com.chua.starter.spider.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充。
 *
 * <p>框架未提供全局 {@link MetaObjectHandler}，这里按 {@code SysBase}
 * 字段约定在新增/更新时自动填充创建人与时间信息，
 * 避免各业务模块重复赋值。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class SpiderMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充创建时间、更新时间。
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * 更新时填充更新时间。
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
