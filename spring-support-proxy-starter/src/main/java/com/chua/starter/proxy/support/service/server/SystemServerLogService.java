package com.chua.starter.proxy.support.service.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerLog;

import java.time.LocalDateTime;

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

    void asyncRecord(SystemServerLog log);
}





