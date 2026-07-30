package com.chua.starter.protocol.support.service.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统服务器运行时日志（生命周期日志）服务。
 * <p>
 * 日志以文件方式落地，用于记录启动/停止/重启/异常等运维关注事件。
 *
 * @author CH
 */
public interface SystemServerRuntimeLogService {

    /**
     * 写入一条运行时日志。
     *
     * @param serverId 服务ID
     * @param level 日志级别（INFO/WARN/ERROR）
     * @param event 事件类型（START/STOP/RESTART/CONFIG/APPLY/ERROR等）
     * @param message 日志内容
     */
    void append(Integer serverId, String level, String event, String message);

    /**
     * 分页查询运行时日志（从文件读取）。
     */
    ReturnResult<IPage<Map<String, Object>>> page(Page<?> page,
                                                 Integer serverId,
                                                 String level,
                                                 String event,
                                                 LocalDateTime startTime,
                                                 LocalDateTime endTime,
                                                 String keyword);
}

