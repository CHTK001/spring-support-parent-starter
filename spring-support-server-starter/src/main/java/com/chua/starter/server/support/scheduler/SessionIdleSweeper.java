package com.chua.starter.server.support.scheduler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.model.vo.TerminalOnlineVO;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.spi.TerminalSessionChannelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 终端会话空闲清理任务
 * <p>
 * 由 {@link ServerTaskScheduler} 时间轮驱动，周期扫描全部远控会话通道
 * （guacd 中转与各直连通道），关闭超过空闲阈值的远控连接，
 * 防止浏览器异常断开后目标主机侧资源长期占用。
 * </p>
 *
 * <p>
 * 同时做审计对账：服务重启会丢掉全部内存隧道而审计表仍留着 OPEN 行，
 * 空闲扫描看不到它们，需要按"在线通道的记录标识"反向闭环。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionIdleSweeper {

    /**
     * 孤儿记录判定宽限（分钟），需大于调度周期，避免关掉刚建立、尚未登记到通道的会话
     */
    private static final int ORPHAN_GRACE_MINUTES = 2;

    /**
     * 会话通道注册中心
     */
    private final TerminalSessionChannelRegistry sessionChannels;

    /**
     * 会话审计服务
     */
    private final TerminalSessionService sessionService;

    /**
     * 服务属性（空闲阈值）
     */
    private final ServerProperties properties;

    /**
     * 执行空闲扫描
     */
    public void sweep() {
        int closed = sessionChannels.sweepIdle(properties.getSessionIdleMinutes());
        if (closed > 0) {
            log.info("[terminal] 空闲清理完成，关闭 {} 个会话", closed);
        }
        reconcileOrphanRecords();
    }

    /**
     * 闭环无隧道对应的 OPEN 审计记录
     */
    private void reconcileOrphanRecords() {
        try {
            LocalDateTime snapshotAt = LocalDateTime.now();
            Set<Long> liveRecordIds = liveRecordIds();
            ReturnResult<List<MsTerminalSession>> opened = sessionService.listByStatus(ServerConstants.SESSION_OPEN);
            if (!opened.isSuccess() || opened.getData() == null) {
                return;
            }
            for (MsTerminalSession record : opened.getData()) {
                if (isOrphan(record, liveRecordIds, snapshotAt)) {
                    sessionService.closeRecord(record.getId(), "会话隧道已中断");
                    log.info("[terminal] 对账闭环孤儿会话记录 id={} protocol={} user={}",
                            record.getId(), record.getMsTerminalSessionProtocol(), record.getMsTerminalSessionUser());
                }
            }
        } catch (Exception e) {
            log.warn("[terminal] 会话审计对账失败: {}", e.getMessage());
        }
    }

    /**
     * 判定记录是否为孤儿：内存中无对应隧道，且连接时间早于本次快照的宽限边界
     *
     * @param record       审计记录
     * @param liveRecordIds 在线通道的记录标识集合
     * @param snapshotAt   在线快照时刻
     * @return true 表示可闭环
     */
    private boolean isOrphan(MsTerminalSession record, Set<Long> liveRecordIds, LocalDateTime snapshotAt) {
        if (record.getId() == null || liveRecordIds.contains(record.getId())) {
            return false;
        }
        LocalDateTime connectTime = record.getMsTerminalSessionConnectTime();
        if (connectTime == null) {
            return false;
        }
        return connectTime.isBefore(snapshotAt.minusMinutes(ORPHAN_GRACE_MINUTES));
    }

    /**
     * 取当前各通道在线会话的记录标识
     *
     * @return 记录标识集合
     */
    private Set<Long> liveRecordIds() {
        Set<Long> ids = new HashSet<>();
        for (TerminalOnlineVO vo : sessionChannels.online()) {
            if (vo.recordId() != null) {
                ids.add(vo.recordId());
            }
        }
        return ids;
    }
}
