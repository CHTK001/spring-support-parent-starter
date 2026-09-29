package com.chua.starter.monitor.support.provider.server;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.ssh.support.client.LinuxExecClient;
import com.chua.ssh.support.client.SshClient;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricProvider;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 服务器终端采集 提供者(SSH)
 * <p>
 * 通过 SSH 在目标服务器执行自定义命令, 原始输出存于 {@link CollectResult#rawOutput},
 * 由调度器结合任务关联的解析规则生成指标点。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Spi(value = "server-terminal", order = 100)
public class ServerTerminalProvider implements MetricProvider {

    /**
     * 默认 SSH 端口
     */
    private static final int DEFAULT_SSH_PORT = 22;

    /**
     * 默认命令超时(毫秒)
     */
    private static final int DEFAULT_TIMEOUT_MS = 5_000;

    /**
     * 本采集通道的类型标识。
     *
     * <p>用于把采集结果归到「终端 / SSH」这一通道名下，与其它采集通道（如 HTTP、Agent）
     * 区分开。</p>
     *
     * @return 恒为 {@link ProviderType#TERMINAL}
     */
    @Override
    public ProviderType providerType() {
        return ProviderType.TERMINAL;
    }

    /**
     * 本通道可采集的服务类型。
     *
     * <p>走 SSH 到目标机执行命令采集，因此凡是在目标机上以进程/客户端形式存在的服务
     * 都能覆盖。{@link ServiceType#SERVER} 表示主机整体指标（负载、内存、磁盘等）。</p>
     *
     * @return 支持的服务类型集合，始终非空
     */
    @Override
    public Set<ServiceType> supportServiceTypes() {
        return Set.of(ServiceType.SERVER, ServiceType.REDIS, ServiceType.MYSQL,
                ServiceType.PG, ServiceType.ORACLE, ServiceType.MSSQL, ServiceType.MQ);
    }

    /**
     * 采集入口（响应式包装）。
     *
     * <p>真正的采集逻辑在 {@code doCollect}，其中是阻塞的 SSH 调用，
     * 因此这里调度到 {@link Schedulers#boundedElastic()}，避免占用事件循环线程。</p>
     *
     * @param context 采集上下文，含目标连接信息与要采的服务类型
     * @return 包含单条采集结果的 {@link Mono}；执行过程中的异常以错误信号返回
     */
    @Override
    public Mono<CollectResult> collect(CollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步执行 SSH 命令
     *
     * @param context 采集上下文
     * @return 采集结果
     */

    private CollectResult doCollect(CollectContext context) {
        CollectResult result = new CollectResult();
        result.setTask(context.getTask());
        result.setMonitor(context.getMonitor());

        JsonObject config = Json.getJsonObject(context.getTask().getCollectConfig());
        String command = str(config.getObject("command"));
        if (command == null || command.isEmpty()) {
            return result.fail("collect_config 缺少 command");
        }

        String host = str(config.getObject("host"));
        String username = str(config.getObject("username"));
        String password = str(config.getObject("password"));
        Integer port = intOrNull(config.getObject("port"));

        if (host == null || username == null) {
            return result.fail("collect_config 缺少 host/username");
        }

        int timeout = context.getTask().getMonitorTaskTimeout() == null
                ? DEFAULT_TIMEOUT_MS : context.getTask().getMonitorTaskTimeout();

        /**
         * 尝试。
         * @param password 密码
         */
        try (LinuxExecClient client = buildClient(host, port, username, password)) {
            client.connect();
            SshClient.ExecResult exec = client.executeCommand(command, timeout);
            result.setRawOutput(exec.stdout());
            if (exec.exitCode() != 0) {
                result.fail("命令退出码非 0: " + exec.exitCode() + ", stderr=" + exec.stderr());
            } else {
                result.setSuccess(true);
            }
        } catch (Exception e) {
            log.error("[ServerTerminalProvider] SSH 执行失败 task={} host={}", context.getTask().getId(), host, e);
            result.fail(e.getMessage());
        }
        return result;
    }

    /**
     * 构造 Linux执行客户端
     *
     * @param host     主机
     * @param port     端口
     * @param username 用户名
     * @param password 密码
     * @return 客户端实例
     */
    private LinuxExecClient buildClient(String host, Integer port, String username, String password) {
        ClientSetting setting = ClientSetting.builder()
                .host(host)
                .port(port == null ? DEFAULT_SSH_PORT : port)
                .username(username)
                .password(password == null ? "" : password)
                .connectTimeout(10_000L)
                .readTimeout(30_000L)
                .writeTimeout(30_000L)
                .build();
        /**
         * Linux执行客户端。
         * @param setting setting
         * @return Linux执行客户端的结果
         */
        return new LinuxExecClient(setting);
    }

    /**
     * 对象转字符串
     *
     * @param o 对象
     * @return 字符串, 空 时返回 空
     */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 对象转 Integer
     *
     * @param o 对象
     * @return Integer, 转换失败返回 空
     */
    private static Integer intOrNull(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
