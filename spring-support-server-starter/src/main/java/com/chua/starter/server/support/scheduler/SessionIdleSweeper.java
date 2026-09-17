package com.chua.starter.server.support.scheduler;

import com.chua.starter.server.support.config.ServerProperties;
import com.chua.starter.server.support.terminal.GuacdSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
* 终端会话空闲清理任务
* <p>
* 由 {@link ServerTaskScheduler} 时间轮驱动，周期扫描 guacd 会话注册表，
* 关闭超过空闲阈值的远控连接，防止浏览器异常断开后 guacd 侧资源长期占用。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionIdleSweeper {

    /** 会话注册表 */
    private final GuacdSessionRegistry sessionRegistry;

    /** 服务属性（空闲阈值） */
    private final ServerProperties properties;

    /**
    * 执行空闲扫描
    */
    public void sweep() {
        int closed = sessionRegistry.sweepIdle(properties.getSessionIdleMinutes());
        if (closed > 0) {
            log.info("[terminal] 空闲清理完成，关闭 {} 个会话", closed);
        }
    }
}
