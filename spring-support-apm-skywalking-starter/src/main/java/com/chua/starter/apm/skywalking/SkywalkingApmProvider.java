package com.chua.starter.apm.skywalking;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.apm.support.spi.ApmProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * SkyWalking 的 {@link ApmProvider} 实现。
 * <p>
 * 只做两件事：声明自己是 {@code skywalking}，并把 utils 层
 * {@code utils-support-skywalking-starter} 里的 {@code OtnlEngine}（SPI 名 {@code skywalking}）
 * 交给上层。真正的 OAP GraphQL 查询全部在 {@code SkywalkingOtnlEngine} 里。
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
}
