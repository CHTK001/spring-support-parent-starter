package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * Nginx 访问日志 Top 统计。
 *
 * <p>数据来源是 SSH 拉取 access log 尾部后<strong>在应用内解析</strong>，不入库。
 * 理由：日志行数不可控（高流量站每分钟几十万行），入库会迅速吃掉磁盘；
 * 而 Top N 只需要一个统计结果，没有留存价值。</p>
 *
 * <p><strong>解析的是「尾部 N 行」而非「时间窗内全部行」</strong>：远端 tail 有天然上限
 * （{@code lines} 参数，最大 20000），避免把整台机器的日志拖回来。
 * 代价是统计口径是「最近 N 行」而不是「最近 N 分钟」，
 * 低流量时两者接近，高流量时最近 N 行只覆盖很短的时间段——
 * 这一点在 {@code message} 里对用户说明，不假装是时间窗统计。</p>
 *
 * <p>状态码按「类别」聚合（2xx/3xx/4xx/5xx）而不是精确码，
 * 因为 Top 榜的价值是看比例分布，精确码会散成几十行。</p>
 *
 * @param total    成功解析的行数（解析失败与不匹配格式的行不计入）
 * @param scanned  实际扫描的总行数
 * @param paths    路径 Top N
 * @param ips      客户端 IP Top N
 * @param statuses 状态码类别分布
 * @param message  面向用户的说明（无数据 / 日志为空时说明原因）
 * @author CH
 * @since 4.0.0
 */
public record NginxAccessTopVO(
        int total,
        int scanned,
        List<Item> paths,
        List<Item> ips,
        List<StatusItem> statuses,
        String message
) {

    /**
     * Top 条目。
     *
     * @param key  维度取值：路径 / IP
     * @param hits 出现次数
     */
    public record Item(String key, long hits) {
    }

    /**
     * 状态码类别条目。
     *
     * @param category 类别：2xx / 3xx / 4xx / 5xx / other
     * @param hits     出现次数
     * @param pct      占已解析行数的百分比（一位小数）
     */
    public record StatusItem(String category, long hits, double pct) {
    }

    /**
     * 构造「无数据」结果。
     *
     * @param scanned 扫描行数
     * @param message 说明
     * @return 统计结果
     */
    public static NginxAccessTopVO empty(int scanned, String message) {
        return new NginxAccessTopVO(0, scanned, List.of(), List.of(), List.of(), message);
    }
}
