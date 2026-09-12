package com.chua.starter.server.support.service;

import com.chua.starter.server.support.entity.MsProxy;

import java.util.List;

/**
* 免费代理服务：自研抓取（databay 可用源）→ 清空代理表 → 录入；页面从表读取
* @author CH
* @since 4.0.0
 */
public interface ProxyService {

    /**
    * 获取免费代理（抓取→清空表→录入——返回新列表——较慢）
    *
    * @return 免费代理列表
     */
    List<MsProxy> fetchFreeProxies();

    /**
    * 免费代理列表（从数据表读取——快）
    *
    * @return 免费代理列表
     */
    List<MsProxy> listProxies();

    /**
    * 当前代理（自动——表内第一个——业务无需手动指定）
    *
    * @return "host:port"，无则 空
     */
    String currentProxy();
}
