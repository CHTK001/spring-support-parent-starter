package com.chua.starter.spider.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 爬虫模块服务公共基类。
 *
 * <p>公共创建/更新时间字段由 {@code SpiderMetaObjectHandler} 自动填充，
   * 服务 层无需手动维护时间戳，仅继承 MyBatis-Plus 标准 CRUD 能力。</p>
 *
 * @param <T> 实体类型
 * @since 4.0.0.42
 * @author CH
 */
public interface SpiderBaseService<T extends SysBase> extends IService<T> {
}