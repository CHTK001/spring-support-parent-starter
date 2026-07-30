package com.chua.starter.protocol.support.service.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.network.protocol.firewall.FirewallMetrics;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServerLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SystemServerLogService extends IService<SystemServerLog> {

    ReturnResult<IPage<SystemServerLog>> pageLogs(Page<SystemServerLog> page,
                                                  Integer serverId,
                                                  String filterType,
                                                  String processStatus,
                                                  String clientIp,
                                                  LocalDateTime startTime,
                                                  LocalDateTime endTime);

    ReturnResult<byte[]> exportCsv(Integer serverId,
                                   String filterType,
                                   String processStatus,
                                   String clientIp,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime);

    ReturnResult<Integer> cleanup(LocalDateTime beforeTime);

    ReturnResult<List<Map<String, Object>>> queryFirewallEvents(Integer serverId,
                                                                String clientIp,
                                                                String action,
                                                                LocalDateTime startTime,
                                                                LocalDateTime endTime,
                                                                Integer limit);

    ReturnResult<FirewallMetrics> queryFirewallMetrics(Integer serverId,
                                                       LocalDateTime startTime,
                                                       LocalDateTime endTime);

    void asyncRecord(SystemServerLog log);
}

