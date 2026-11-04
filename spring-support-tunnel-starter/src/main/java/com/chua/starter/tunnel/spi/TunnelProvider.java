package com.chua.starter.tunnel.spi;

import com.chua.starter.common.support.tunnel.TunnelRecord;

import java.util.List;
import java.util.Map;

/**
 * 内网穿透提供方 SPI —— 一个接口，多个实现。
 *
 * <p>设计对齐 {@code com.chua.starter.queue.spi.MessageDispatcherProvider}：
 * 本模块只定义「穿透能力」契约，不包含任何厂商客户端；ngrok、Cloudflare Tunnel
 * 等实现各自放在独立 starter 里，业务方引入哪个 jar 就具备哪个提供方，
 * 通过 {@code plugin.tunnel.providers} 决定启用哪些。</p>
 *
 * <p>实现类只需声明为 Spring Bean（或在自动配置里 {@code @Bean} 注册），
 * 由 {@link TunnelProviderRegistry} 自动收集；REST 入口、持久化、自动恢复、
 * SSE 推送等公共逻辑全部由本模块统一提供，实现方不重复实现。</p>
 *
 * <p>由此页面只面向一套 {@code /ms/tunnel} 接口，切换提供方只是切换
 * 一个字段，不需要为每个提供方维护一套 URL。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface TunnelProvider {

    /**
     * 提供方标识，必须全局唯一且稳定（写入 {@link TunnelRecord#getProvider()}，
     * 也是存储子目录名）。
     *
     * @return 提供方标识，例如 {@code ngrok} / {@code cloudflared}
     */
    String name();

    /**
     * 展示名称，用于页面下拉与能力列表。
     *
     * @return 展示名称，缺省回退 {@link #name()}
     */
    default String displayName() {
        return name();
    }

    /**
     * 当前运行环境是否具备执行条件（客户端 jar 已引入、外部可执行文件存在等）。
     *
     * <p>注意与「配置是否完整」区分：这里只回答「能不能跑」，凭据缺失属于
     * {@link #capabilities()} 与启动时的错误信息。返回 {@code false} 时页面
     * 应禁用该提供方，而不是让用户点下去才失败。</p>
     *
     * @return 可用返回 true
     */
    boolean isAvailable();

    /**
     * 支持的隧道模式（如 ngrok 的 {@code http/tcp/tls}、cloudflared 的
     * {@code quick/named}），用于页面渲染模式下拉。
     *
     * @return 模式列表
     */
    List<String> supportedModes();

    /**
     * 新建映射时的默认模式。
     *
     * @return 默认模式，必须包含在 {@link #supportedModes()} 中
     */
    String defaultMode();

    /**
     * 该提供方的持久化目录覆盖值；返回 {@code null} 时使用
     * {@code <plugin.tunnel.store-dir>/<name>}。
     *
     * <p>保留这个覆盖点是为了兼容既有配置 {@code plugin.ngrok.store-dir} /
     * {@code plugin.cloudflared.store-dir}：升级后原有映射文件继续被识别，
     * 不需要人工搬迁。</p>
     *
     * @return 目录路径，或 null 表示沿用统一根目录
     */
    default String storeDir() {
        return null;
    }

    /**
     * 应用重启时是否恢复 {@code autoStart} 的映射。
     *
     * @return 允许恢复返回 true
     */
    default boolean autoStartStored() {
        return true;
    }

    /**
     * 能力探测信息，直接回填给页面（凭据是否配置、可执行文件路径等）。
     *
     * @return 能力键值对
     */
    Map<String, Object> capabilities();

    /**
     * 校验并归一并页面提交的字段，写入 {@code target}。
     *
     * <p>端口范围、名称兜底、备注、开关等公共字段已由
     * {@link com.chua.starter.tunnel.support.TunnelInputs#applyCommon} 处理；
     * 这里只需处理提供方特有的部分：模式合法性、专属字段、
     * 以及依赖配置的交叉校验。</p>
     *
     * @param target 目标记录（已带 id / provider）
     * @param input  页面提交内容
     * @throws IllegalArgumentException 配置非法，消息直接展示给用户
     */
    void applyInput(TunnelRecord target, TunnelRecord input);

    /**
     * 建立隧道。
     *
     * <p>实现应在此完成客户端初始化与连接，并返回可用于取公网地址、
     * 可关闭的会话。失败时抛出异常，消息即页面展示的失败原因（编排层会
     * 写入该映射的 {@code error} 字段）。</p>
     *
     * @param record 已校验并持久化的映射
     * @return 隧道会话
     */
    TunnelSession open(TunnelRecord record);
}
