package com.chua.starter.common.support.cache;

import org.springframework.cache.annotation.EnableCaching;

/**
* 缓存代理配置
* <p>
* 自定义缓存操作源与解析器统一由 {@link CacheConfiguration} 提供（@Primary，无条件创建）。
* 本类仅保留 @enable缓存，作为未显式声明 @enable缓存 的应用的兜底入口；
* 若在此重复定义 习俗缓存operation源 会导致与 缓存配置 同名 Bean 冲突。
* </p>
*
* @版本 1.0.0
* @since 2025/6/6
* @author CH
 */
@EnableCaching
public class CacheProxyCachingConfiguration {
}
