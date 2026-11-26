package com.chua.starter.common.support.api.sniff;

import com.chua.common.support.network.server.filter.security.BotSniffGuard;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 嗅探判定事件（跨模块通知）。
 *
 * <p>spring-common 只负责判定与拦截，<b>不落库</b>；需要记录审计日志的业务模块
 * （如 system-starter 的操作日志）监听本事件即可。事件携带判定结论与请求上下文，
 * 不含请求体，避免把敏感数据写进日志。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Getter
public class BotSniffEvent extends ApplicationEvent {

    /**
     * 判定结论：CHALLENGE / BLOCK
     */
    private final BotSniffGuard.Decision decision;

    /**
     * DetectorUtils 判定档位：HUMAN / UNKNOWN / SUSPICIOUS / BOT
     */
    private final String verdict;

    /**
     * 判定原因
     */
    private final String reason;

    /**
     * 启发式得分
     */
    private final int score;

    /**
     * 请求方法
     */
    private final String method;

    /**
     * 请求路径（不含查询串）
     */
    private final String path;

    /**
     * 客户端地址
     */
    private final String clientIp;

    /**
     * User-Agent
     */
    private final String userAgent;

    /**
     * 是否签发了 ticket（解答通过时才有）
     */
    private final boolean ticketIssued;

    /**
     * 构造事件。
     *
     * @param source       事件源
     * @param decision     判定结论
     * @param verdict      判定档位
     * @param reason       判定原因
     * @param score        启发式得分
     * @param method       请求方法
     * @param path         请求路径
     * @param clientIp     客户端地址
     * @param userAgent    User-Agent
     * @param ticketIssued 是否签发 ticket
     */
    public BotSniffEvent(Object source, BotSniffGuard.Decision decision, String verdict, String reason,
                         int score, String method, String path, String clientIp,
                         String userAgent, boolean ticketIssued) {
        super(source);
        this.decision = decision;
        this.verdict = verdict;
        this.reason = reason;
        this.score = score;
        this.method = method;
        this.path = path;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.ticketIssued = ticketIssued;
    }

    /**
     * 是否被拒绝。
     *
     * @return 拒绝返回 true
     */
    public boolean blocked() {
        return BotSniffGuard.Decision.BLOCK == decision;
    }

    /**
     * 是否下发了挑战。
     *
     * @return 挑战返回 true
     */
    public boolean challenged() {
        return BotSniffGuard.Decision.CHALLENGE == decision;
    }
}
