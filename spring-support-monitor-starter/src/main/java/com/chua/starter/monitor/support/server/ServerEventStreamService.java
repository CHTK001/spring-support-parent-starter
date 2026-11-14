package com.chua.starter.monitor.support.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
import com.chua.starter.monitor.support.service.ServerManageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 服务管理 —— 全局状态变更探测（服务端，仅在变化时推送）。
 *
 * <p><b>这不是短轮询</b>：短轮询指客户端定时重复请求数据接口。本页客户端只持有
 * {@code POST /ms/monitor/server/stream} 一条 SSE 长连接，零定时拉取。本类在
 * <b>服务端进程内</b>比对节点视图的状态指纹，仅当指纹变化时才经
 * {@link ServerEventStreamRegistry} 广播一帧 {@code changed}；空闲时既不发一条
 * HTTP、也不查一次库（无订阅者直接返回），前端只在收到帧后才重取数据。</p>
 *
 * <p><b>为什么需要服务端比对</b>：节点状态来自 {@link ServerNodeRegistry} 里
 * {@code Server.isRunning()} 的实时值，进程被杀、端口被占等自发漂移没有事件源可监听，
 * 也无从在业务方法里逐一埋点。一次内存比对同时覆盖两类来源：用户动作（增删改启停）
 * 与运行态自发变化，且天然服务所有连接的标签页。{@code list()} 走的是既有装配过程，
 * 按 id 升序、字段顺序稳定（config 由 {@code LinkedHashMap} 解析），序列化结果可作为
 * 确定性指纹。</p>
 *
 * <p><b>空跑保护</b>：无订阅者时直接返回，不查库、不计算指纹；{@code lastSignature}
 * 保持不变，于是第一个连接的页面在下一轮必然收到一帧（相当于一次重连补拉），
 * 首屏加载失败也能在下一轮静默重取里自愈。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerEventStreamService {

    /**
     * 指纹比对间隔（毫秒）。服务端进程内比对，5s 让多标签页同步比原先 10s 短轮询更快，
     * 且仅在变化时推帧 —— 空闲时零请求，与「客户端每 10s 打两个接口」有本质区别。
     */
    private static final long DETECT_INTERVAL_MS = 5_000L;

    private final ServerManageService serverManageService;
    private final ServerEventStreamRegistry streamRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 上一次广播时的状态指纹。volatile：调度线程单线程写，但仍显式保证可见性。
     */
    private volatile String lastSignature;

    /**
     * 周期探测：仅在有订阅者且状态指纹变化时广播一次。
     */
    @Scheduled(fixedDelay = DETECT_INTERVAL_MS, initialDelay = DETECT_INTERVAL_MS)
    public void detect() {
        if (streamRegistry.subscriberCount() == 0) {
            return;
        }
        try {
            ReturnResult<List<ServerNodeView>> result = serverManageService.list();
            // list() 内部 catch 成 ReturnResult.error（data 为空）：本轮跳过，
            // 不更新指纹，下一轮继续补，避免把一次查询故障误判成「状态没变」。
            if (result == null || !result.isOk() || result.getData() == null) {
                return;
            }
            String signature = objectMapper.writeValueAsString(result.getData());
            boolean changed = !signature.equals(lastSignature);
            lastSignature = signature;
            if (changed) {
                streamRegistry.broadcast(Map.of("reason", "nodes-changed"));
            }
        } catch (JsonProcessingException | RuntimeException e) {
            log.debug("[server-sse] 状态探测跳过: {}", e.getMessage());
        }
    }
}
