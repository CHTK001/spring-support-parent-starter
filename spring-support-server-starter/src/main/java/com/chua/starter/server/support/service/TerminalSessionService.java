package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsTerminalSession;

import java.util.List;

/**
* 终端会话审计服务
* <p>
* 记录远控会话的开闭生命周期，并提供管理端查询与强制关闭能力。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
public interface TerminalSessionService {

    /**
    * 写入会话打开记录
    *
    * @param hostId     主机 标识
    * @param hostName   主机名称（冗余）
    * @param protocol   协议名
    * @param userName   发起用户
    * @param clientIp   客户端 IP
    * @return 已持久化的记录，写入失败时返回 空（不阻断连接）
    */
    MsTerminalSession openRecord(Long hostId, String hostName, String protocol,
                                 String userName, String clientIp);

    /**
    * 闭环会话记录
    *
    * @param recordId 记录 标识
    * @param reason   关闭原因
    */
    void closeRecord(Long recordId, String reason);

    /**
    * 查询最近会话记录（默认最新 200 条）
    *
    * @return 会话列表
    */
    ReturnResult<List<MsTerminalSession>> listRecent();

    /**
    * 按状态过滤会话记录
    *
    * @param status 打开 / 关闭
    * @return 会话列表
    */
    ReturnResult<List<MsTerminalSession>> listByStatus(String status);
}
