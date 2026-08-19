package com.chua.starter.spider.service;

import com.chua.starter.spider.entity.SpiderDefinition;

import java.util.List;

/**
 * 爬虫定义服务。
 *
 * <p>提供爬虫定义的 CRUD 与启用列表查询，启用列表经缓存加速，
 * 供定时调度每秒轮询命中；写操作统一清理全部缓存项。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SpiderDefinitionService extends SpiderBaseService<SpiderDefinition> {

    /**
     * 查询全部已启用的爬虫定义。
     *
     * <p>命中 {@code spider:definitions:enabled} 缓存；写操作后缓存整体失效。</p>
     *
     * @return 已启用定义列表
     */
    List<SpiderDefinition> listEnabled();

    /**
     * 仅回写最近执行时间，不触发缓存清理。
     *
     * <p>供调度器定时回写 {@code last_run_at} 使用，绕过 {@code @CacheEvict}
     * 全量失效，避免每秒调度反复击穿启用列表缓存。</p>
     *
     * @param id        主键
     * @param lastRunAt 最近执行时间
     * @return 是否成功
     */
    boolean markRunAtOnly(Long id, java.time.LocalDateTime lastRunAt);
}