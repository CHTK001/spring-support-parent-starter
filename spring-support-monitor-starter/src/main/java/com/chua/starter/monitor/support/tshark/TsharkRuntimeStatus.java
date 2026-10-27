package com.chua.starter.monitor.support.tshark;

/**
 * tshark 运行时状态快照。
 *
 * <p>同时用于三处：{@code GET /tshark/status} 的返回值、SSE 推给页面的进度帧、
 * 以及启动自检失败时写进日志的摘要。字段刻意保持扁平，前端不必再拼装。</p>
 *
 * <p><b>为什么把「可解析」和「可抓包」拆成两个布尔</b>：便携版 tshark 解压即能读 pcap，
 * 但实时抓包还依赖抓包驱动（Windows 上是 Npcap，Linux 上是 libpcap 与权限）。
 * 只报一个 ready 会让页面在「解析得了、抓不了」时给不出可执行的提示。</p>
 *
 * @param state        当前状态
 * @param progress     进度百分比 0-100（非下载阶段为 0 或 100）
 * @param message      面向运维的一句话说明
 * @param ready        tshark 可执行且版本探测通过（可解析 / 导入 pcap）
 * @param captureReady 存在可用抓包接口（可实时抓包）
 * @param binPath      实际使用的可执行文件路径
 * @param version      {@code tshark --version} 的第一行
 * @param source       可执行文件的来源：env / property / installed / system / path / ""
 * @param updatedAt    状态最后更新时间（epoch 毫秒）
 * @author CH
 * @since 4.0.0.42
 */
public record TsharkRuntimeStatus(
        TsharkInstallState state,
        int progress,
        String message,
        boolean ready,
        boolean captureReady,
        String binPath,
        String version,
        String source,
        long updatedAt) {

    /**
     * 构造「未就绪」状态，供启动前 / 探测异常时使用。
     *
     * @param state   状态
     * @param message 说明
     * @return 状态快照
     */
    public static TsharkRuntimeStatus of(TsharkInstallState state, String message) {
        return new TsharkRuntimeStatus(state, 0, message, false, false, "", "", "", System.currentTimeMillis());
    }
}
