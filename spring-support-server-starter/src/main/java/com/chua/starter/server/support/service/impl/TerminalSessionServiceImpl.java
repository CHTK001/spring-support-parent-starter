package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.mapper.MsTerminalSessionMapper;
import com.chua.starter.server.support.service.TerminalSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
* 终端会话审计服务实现
* <p>
* 审计写入遵循"不阻断主链路"原则：记录失败仅告警，不影响远控连接建立。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalSessionServiceImpl implements TerminalSessionService {

    /** 最近查询条数上限 */
    private static final int RECENT_LIMIT = 200;

    /** 会话 映射器 */
    private final MsTerminalSessionMapper sessionMapper;

    @Override
    public MsTerminalSession openRecord(Long hostId, String hostName, String protocol,
                                        String userName, String clientIp) {
        try {
            var record = new MsTerminalSession();
            record.setMsTerminalSessionHostId(hostId);
            record.setMsTerminalSessionHostName(hostName);
            record.setMsTerminalSessionProtocol(protocol);
            record.setMsTerminalSessionUser(userName);
            record.setMsTerminalSessionClientIp(clientIp);
            record.setMsTerminalSessionConnectTime(LocalDateTime.now());
            record.setMsTerminalSessionStatus(ServerConstants.SESSION_OPEN);
            sessionMapper.insert(record);
            return record;
        } catch (Exception e) {
            log.warn("[terminal] 会话审计写入失败 hostId={}: {}", hostId, e.getMessage());
            return null;
        }
    }

    @Override
    public void closeRecord(Long recordId, String reason) {
        if (recordId == null) {
            return;
        }
        try {
            MsTerminalSession record = sessionMapper.selectById(recordId);
            if (record == null || ServerConstants.SESSION_CLOSED.equals(record.getMsTerminalSessionStatus())) {
                return;
            }
            record.setMsTerminalSessionStatus(ServerConstants.SESSION_CLOSED);
            record.setMsTerminalSessionCloseTime(LocalDateTime.now());
            record.setMsTerminalSessionCloseReason(reason == null ? "未知" : reason);
            sessionMapper.updateById(record);
        } catch (Exception e) {
            log.warn("[terminal] 会话审计闭环失败 recordId={}: {}", recordId, e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<MsTerminalSession>> listRecent() {
        List<MsTerminalSession> records = sessionMapper.selectList(
                new LambdaQueryWrapper<MsTerminalSession>()
                        .orderByDesc(MsTerminalSession::getId)
                        .last("LIMIT " + RECENT_LIMIT));
        return ReturnResult.list(records);
    }

    @Override
    public ReturnResult<List<MsTerminalSession>> listByStatus(String status) {
        if (!ServerConstants.SESSION_OPEN.equals(status) && !ServerConstants.SESSION_CLOSED.equals(status)) {
            return ReturnResult.error("状态仅支持 OPEN / CLOSED");
        }
        List<MsTerminalSession> records = sessionMapper.selectList(
                new LambdaQueryWrapper<MsTerminalSession>()
                        .eq(MsTerminalSession::getMsTerminalSessionStatus, status)
                        .orderByDesc(MsTerminalSession::getId)
                        .last("LIMIT " + RECENT_LIMIT));
        return ReturnResult.list(records);
    }
}
