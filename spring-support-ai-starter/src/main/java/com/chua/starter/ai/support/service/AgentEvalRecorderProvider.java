package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.agent.AgentEvalHook;
import com.chua.common.support.ai.agent.JdbcAgentEvalRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Agent 评测记录器提供者：进程内懒加载单例 {@link JdbcAgentEvalRecorder}。
 *
 * <p>评测落库走 {@link JdbcAgentEvalRecorder}（构造时幂等建表 {@code agent_eval_record}）。
 * 故意不做容器启动期 Bean：建表依赖数据库可用性，启动期强依赖会把「数据库暂不可用」
 * 放大成整个服务起不来；这里延迟到第一次真正构建 Agent 时初始化，失败由调用方
 * 降级为「本次不落评测」，不影响对话主流程。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEvalRecorderProvider {

    /**
     * 数据源（评测记录落库）
     */
    private final DataSource dataSource;

    /**
     * 进程内单例记录器（双重检查锁懒加载）
     */
    private volatile JdbcAgentEvalRecorder recorder;

    /**
     * 取评测记录器（进程内单例；首次调用时初始化并幂等建表）。
     *
     * @return 评测钩子；初始化失败时抛出，由调用方决定降级
     */
    public AgentEvalHook recorder() {
        JdbcAgentEvalRecorder r = recorder;
        if (r == null) {
            synchronized (this) {
                r = recorder;
                if (r == null) {
                    r = new JdbcAgentEvalRecorder(dataSource);
                    recorder = r;
                    log.info("[AgentEval] 评测记录器已就绪 table={}", JdbcAgentEvalRecorder.DEFAULT_TABLE_NAME);
                }
            }
        }
        return r;
    }
}
