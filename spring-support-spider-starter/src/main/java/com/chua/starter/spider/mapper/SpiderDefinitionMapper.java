package com.chua.starter.spider.mapper;

import com.chua.starter.mybatis.mapper.BaseMapperX;
import com.chua.starter.spider.entity.SpiderDefinition;

/**
 * 爬虫定义 Mapper。
 *
 * <p>继承 {@link BaseMapperX} 获得 MyBatis-Plus 通用 CRUD 与分页能力；
 * service 层另暴露 {@code markRunAtOnly} 走基类 {@code update} 直接回写执行时间，
 * 不触发 {@code @CacheEvict} 缓存清理，避免调度器每秒清空启用列表缓存。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SpiderDefinitionMapper extends BaseMapperX<SpiderDefinition> {
}