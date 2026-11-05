// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.apm.support.spi;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.apm.model.OtnlStatus;

/**
 * APM 提供者 SPI。
 * <p>
 * 分层约定（与 {@code spring-support-queue-starter} 的 SPI 风格一致）：
 * </p>
 * <ul>
 *   <li>底层数据访问契约在 utils 层的 {@link OtnlEngine}（
 *       {@code skywalking} / {@code opentelemetry} / {@code hotspot} 各实现一份）；</li>
 *   <li>本接口在 spring 层只做「发现 + 选择 + 适配」，把 {@link OtnlEngine} 交给
 *       {@code ApmDispatcher} 统一对外；</li>
 *   <li>每个 {@code spring-support-apm-xx-starter} 实现一个 {@code ApmProvider}，
 *       并在 {@code META-INF/extensions/com.chua.starter.apm.support.spi.ApmProvider}
 *       中登记（{@code 名称=实现类}）。</li>
 * </ul>
 * <p>
 * 业务侧只依赖本接口与 {@link OtnlEngine}，不直接依赖任何具体 APM 的 SDK，
 * 因此新增一种 APM 只需新增一个扩展模块。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ApmProvider extends AutoCloseable {

    /**
     * 提供者名称，与配置 {@code plugin.apm.type} 对应
     *
     * @return 名称，如 {@code skywalking}
     */
    String name();

    /**
     * 优先级：值越小越优先。自动探测时取第一个可用者。
     *
     * @return 优先级
     */
    default int order() {
        return 100;
    }

    /**
     * 底层统一数据面引擎
     *
     * @return 引擎，当前环境不具备该 APM 时返回 null
     */
    OtnlEngine engine();

    /**
     * 当前环境是否真的具备该 APM（装了 agent / 连得上后端 / 配置未关闭）
     *
     * @return 是否可用
     */
    default boolean isAvailable() {
        OtnlEngine engine = engine();
        return engine != null && engine.status().available();
    }

    /**
     * 当前状态（可用性、版本、后端地址、失败原因）
     *
     * @return 状态
     */
    default OtnlStatus status() {
        OtnlEngine engine = engine();
        if (engine == null) {
            return OtnlStatus.unavailable(name(), "当前环境未引入该 APM 模块实现");
        }
        return engine.status();
    }

    /**
     * 运行模式：agent（进程内）/ remote（远端后端）/ none
     *
     * @return 模式
     */
    default String mode() {
        OtnlStatus status = status();
        return status.mode() == null ? "none" : status.mode();
    }

    /**
     * 释放引擎资源，默认释放底层引擎
     */
    @Override
    default void close() {
        OtnlEngine engine = engine();
        if (engine != null) {
            engine.close();
        }
    }
}