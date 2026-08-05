package com.chua.starter.spider.service;

import com.chua.starter.spider.entity.SpiderDefinition;

import java.util.List;

/**
 * 爬虫定义服务。
 *
 * <p>提供爬虫定义的 CRUD 与启用列表查询，启用列表经缓存加速，
 * 供定时调度每秒轮询命中；写操作统一清理全部缓存项。</p>
 *
 * @author CH
 * @since 4.0.0.42
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
}