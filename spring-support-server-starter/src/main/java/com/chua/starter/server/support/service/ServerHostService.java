package com.chua.starter.server.support.service;

import com.chua.starter.server.support.entity.ServerHost;
import java.util.List;
import java.util.Map;

public interface ServerHostService {

    /**
     * 按关键字、类型与启用状态查询服务器列表。
     */
    List<ServerHost> listHosts(String keyword, String serverType, Boolean enabled);

    /**
     * 汇总服务器数量、启用状态与本机/远程分布。
     */
    Map<String, Object> getSummary();

    /**
     * 读取单个服务器配置。
     */
    ServerHost getHost(Integer id);

    /**
     * 新增或更新服务器配置。
     */
    ServerHost saveHost(ServerHost host);

    /**
     * 切换服务器启用状态。
     */
    ServerHost updateEnabled(Integer id, Boolean enabled);

    /**
     * 删除服务器配置。
     */
    void deleteHost(Integer id);
}
