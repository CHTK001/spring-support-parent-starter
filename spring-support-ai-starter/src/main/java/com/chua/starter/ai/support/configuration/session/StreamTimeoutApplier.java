package com.chua.starter.ai.support.configuration.session;

import com.chua.common.support.ai.chat.aggregate.FailoverTimeouts;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 把设置中心里的「流式等待上限」应用到公共故障转移模板。
 *
 * <p><b>为什么必须有这一步</b>：{@link FailoverTimeouts} 的两个上限（首包 8s / 整段 100s）
 * 决定了「一条流最多能沉默多久」。而「会话超时时间」要收口的正是沉默的流 ——
 * 上限比会话超时时，轮不到它生效，配置项就只是个摆设。所以两者必须同时可配，
 * 且默认值的相对关系要讲得清楚：默认 8s/100s 都远小于默认 30 分钟会话超时，
 * 即「会话超时」默认是兜底而非常规路径；把等待上限调大后它才会先触发。</p>
 *
 * <p>只在启动时应用一次（值来自全局层）：这两个是**服务端部署级**参数，不是个人偏好 ——
 * 它们描述的是「本机到上游的网络与模型有多慢」，按账号各设一份会让同一条流的等待上限
 * 取决于谁在问，没法排查。因此不在设置中心对普通账号开放。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTimeoutApplier {

    /**
     * 配置分组
     */
    private static final String GROUP = "session";

    /**
     * 首包等待上限字段名
     */
    private static final String FIELD_FIRST_TOKEN = "stream_first_token_timeout_millis";

    /**
     * 整段完成等待上限字段名
     */
    private static final String FIELD_COMPLETION = "stream_completion_wait_seconds";

    /**
     * 全局设置读取器：这两个参数只读全局层（服务端级，不按用户隔离）
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 启动时应用一次。
     */
    @PostConstruct
    public void apply() {
        long firstToken = readPositive(FIELD_FIRST_TOKEN,
                FailoverTimeouts.DEFAULT_FIRST_TOKEN_TIMEOUT_MS);
        long completion = readPositive(FIELD_COMPLETION,
                FailoverTimeouts.DEFAULT_COMPLETION_WAIT_SECONDS);
        FailoverTimeouts.setFirstTokenTimeoutMs(firstToken);
        FailoverTimeouts.setCompletionWaitSeconds(completion);
        log.info("[StreamTimeout] 首包上限={}ms 整段上限={}s（会话超时 {} 分钟）",
                FailoverTimeouts.firstTokenTimeoutMs(),
                FailoverTimeouts.completionWaitSeconds(),
                globalSettingReader.getStrAs(null, GROUP, "stream_idle_timeout_minutes"));
    }

    /**
     * 读一个正整数配置，缺失或非法时返回兜底值。
     *
     * @param field        字段名
     * @param defaultValue 兜底值
     * @return 生效值
     */
    private long readPositive(String field, long defaultValue) {
        // 传 null 给 getStrAs 即「只读全局层」：这两个是服务端部署级参数，不按用户隔离
        String raw = globalSettingReader.getStrAs(null, GROUP, field);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value > 0) {
                return value;
            }
            log.warn("[StreamTimeout] {}={} 不是正数，回落 {}", field, raw, defaultValue);
            return defaultValue;
        } catch (NumberFormatException e) {
            log.warn("[StreamTimeout] {} 非整数（{}），回落 {}", field, raw, defaultValue);
            return defaultValue;
        }
    }
}