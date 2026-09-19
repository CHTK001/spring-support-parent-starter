package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.service.ServerHostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 定时 SSH 采集 提供者（调度）
 * <p>
 * 通过 SSH 连接目标主机采集硬件信息（CPU/内存/磁盘/系统版本/IP）。
 * SSH 为阻塞 IO，在 boundedElastic 调度器上执行，避免阻塞 Web 线程。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledHostCollectProvider implements HostCollectProvider {

    /**
     * 主机服务
    */
    private final ServerHostService hostService;

    @Override
    public HostCollectType type() {
        return HostCollectType.SCHEDULED;
    }

    @Override
    public Mono<CollectOutcome> collect(HostCollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步执行采集
     *
     * @param context 上下文
     * @return 结果
     */
    private CollectOutcome doCollect(HostCollectContext context) {
        if (context == null || context.getHost() == null || context.getHost().getId() == null) {
            return CollectOutcome.fail("主机数据不完整");
        }
        try {
            var result = hostService.detectHardware(context.getHost().getId());
            return result.isOk()
                    ? CollectOutcome.ok("采集成功")
                    : CollectOutcome.fail(result.getMsg());
        } catch (Exception e) {
            log.warn("[collect] SCHEDULED 采集失败 hostId={}: {}",
                    context.getHost().getId(), e.getMessage());
            return CollectOutcome.fail(e.getMessage());
        }
    }
}
