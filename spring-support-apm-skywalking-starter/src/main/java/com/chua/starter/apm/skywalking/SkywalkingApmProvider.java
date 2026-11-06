package com.chua.starter.apm.skywalking;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.skywalking.support.client.SkywalkingSettings;
import com.chua.starter.apm.support.spi.ApmProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SkyWalking 的 {@link ApmProvider} 实现。
 * <p>
 * 只做两件事：声明自己是 {@code skywalking}，并把 utils 层
 * {@code utils-support-skywalking-starter} 里的 {@code OtnlEngine}（SPI 名 {@code skywalking}）
 * 交给上层。真正的 OAP GraphQL 查询全部在 {@code SkywalkingOtnlEngine} 里。
 * </p>
 * <p>
 * 另外实现 {@link #runtimeSettings()} / {@link #applyRuntimeSettings(Map)}：
 * OAP 地址在 utils 层是进程级静态设置（{@link SkywalkingSettings}），改完必须把引擎的
 * 「状态缓存 + OAP 连接」丢掉，否则界面上换了地址、下一次查询还打老 OAP。
 * </p>
 * <p>
 * {@code order=0}：SkyWalking 是主实现（需求 1.4），同一台机器上多种 APM 并存时优先选它。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Spi("skywalking")
public class SkywalkingApmProvider implements ApmProvider {

    /**
     * utils 层引擎的 SPI 名
     */
    private static final String ENGINE_NAME = "skywalking";

    /**
     * 可热改项：OAP 地址
     */
    public static final String KEY_OAP_URL = "oapUrl";

    /**
     * 可热改项：单次查询超时（毫秒）
     */
    public static final String KEY_OAP_TIMEOUT = "oapTimeout";

    private volatile OtnlEngine engine;

    @Override
    public String name() {
        return ENGINE_NAME;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public OtnlEngine engine() {
        OtnlEngine current = engine;
        if (current == null) {
            synchronized (this) {
                current = engine;
                if (current == null) {
                    try {
                        current = ServiceProvider.of(OtnlEngine.class).getExtension(ENGINE_NAME);
                    } catch (Throwable e) {
                        log.debug("[APM] 获取 SkyWalking 引擎失败: {}", e.getMessage());
                        return null;
                    }
                    engine = current;
                }
            }
        }
        return current;
    }

    /**
     * 可热改项：OAP 地址与超时。
     *
     * <p>刻意不放用户名/密码：界面回显明文口令是隐患，而鉴权信息属于部署机密，
     * 由 {@code plugin.apm.skywalking.username/password} 或环境变量提供。</p>
     *
     * @return 键值（值为当前生效值）
     */
    @Override
    public Map<String, String> runtimeSettings() {
        Map<String, String> settings = new LinkedHashMap<>(4);
        settings.put(KEY_OAP_URL, SkywalkingSettings.url());
        settings.put(KEY_OAP_TIMEOUT, String.valueOf(SkywalkingSettings.timeout()));
        return settings;
    }

    /**
     * 应用 OAP 地址/超时，并清掉引擎缓存。
     *
     * <p>{@code SkywalkingSettings.apply} 只在值非空时覆盖，所以这里按「有就改、没有就保持」
     * 逐个下发；{@code enabled} 传当前值，避免顺手把 utils 层的开关拨掉。</p>
     *
     * @param values 待应用的键值
     */
    @Override
    public void applyRuntimeSettings(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String url = values.get(KEY_OAP_URL);
        String timeoutRaw = values.get(KEY_OAP_TIMEOUT);
        long timeout = -1L;
        if (timeoutRaw != null && !timeoutRaw.isBlank()) {
            try {
                timeout = Long.parseLong(timeoutRaw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("「" + KEY_OAP_TIMEOUT + "」必须是整数毫秒，收到: " + timeoutRaw);
            }
            if (timeout <= 0) {
                throw new IllegalArgumentException("「" + KEY_OAP_TIMEOUT + "」必须大于 0 毫秒，收到: " + timeoutRaw);
            }
        }
        if (url != null && !url.isBlank() && !isHttpUrl(url)) {
            throw new IllegalArgumentException("「" + KEY_OAP_URL + "」必须形如 http://127.0.0.1:12800，收到: " + url);
        }
        SkywalkingSettings.apply(url, null, null, timeout, SkywalkingSettings.enabled());
        resetEngine();
        log.info("[APM] SkyWalking 设置已应用：OAP={} 超时={}ms", SkywalkingSettings.url(),
                SkywalkingSettings.timeout());
    }

    /**
     * 丢掉引擎上的状态缓存与 OAP 连接。
     *
     * <p>{@code SkywalkingOtnlEngine} 的 client / executor / status 都是惰性创建的，
     * {@code close()} 只把它们置空，所以关闭后可以继续用同一个实例：下一个请求会按
     * 新的 {@link SkywalkingSettings#url()} 重新建连。</p>
     */
    private void resetEngine() {
        OtnlEngine current = engine;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (Exception e) {
            log.debug("[APM] 重置 SkyWalking 引擎缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 是否是 http/https 地址
     *
     * @param url 地址
     * @return 是否合法
     */
    private boolean isHttpUrl(String url) {
        String value = url.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
