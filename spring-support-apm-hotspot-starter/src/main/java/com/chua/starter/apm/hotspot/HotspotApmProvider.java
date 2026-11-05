// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.apm.hotspot;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.apm.support.spi.ApmProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * 自研 hotspot agent 的 {@link ApmProvider} 实现。
 * <p>
 * 数据来自进程内的 {@code utils-support-runtime-apm}（{@code TraceHandler} 插桩 Span），
 * 无需任何外部后端；未以 {@code -javaagent} 挂载时 {@link #isAvailable()} 为 false，
 * 上层注册表会自动跳过它。
 * </p>
 * <p>
 * {@code order=100}：比 SkyWalking 低一档，作为「没有装商业 APM 时的兜底」。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Spi("hotspot")
public class HotspotApmProvider implements ApmProvider {

    /**
     * utils 层引擎的 SPI 名
     */
    private static final String ENGINE_NAME = "hotspot";

    private volatile OtnlEngine engine;

    @Override
    public String name() {
        return ENGINE_NAME;
    }

    @Override
    public int order() {
        return 100;
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
                        log.debug("[APM] 获取 hotspot 引擎失败: {}", e.getMessage());
                        return null;
                    }
                    engine = current;
                }
            }
        }
        return current;
    }
}