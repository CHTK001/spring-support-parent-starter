package com.chua.starter.spider.service;

import com.chua.starter.spider.entity.SpiderEnv;

import java.util.Map;

/**
 * 环境变量服务。
 *
 * <p>提供环境变量的 CRUD，并支持按启用状态加载为 k-v 映射，
 * 供爬虫节点在运行期做 {@code {{env.KEY}}} 插值替换。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SpiderEnvService extends SpiderBaseService<SpiderEnv> {

    /**
     * 加载全部已启用环境变量为 k-v 映射。
     *
     * @return 环境变量映射
     */
    Map<String, String> mapEnabled();
}