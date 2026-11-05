// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.nginx;

import com.chua.starter.datasourcehub.support.model.NginxAccessTopVO;
import com.chua.starter.datasourcehub.support.model.NginxUrlTrendVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NginxAccessLogParser} 单元测试：combined 解析、路径归一化与二进制噪声过滤。
 *
 * <p>二进制噪声（TLS ClientHello / HTTP/2 PRI 前言 / {@code \x00} 连串）来自
 * 公网 80 端口被扫描器乱打的真实日志，2026-10-10 用户反馈「URL 访问数据怎么都是 \x00」——
 * 这组用例把「噪声不进任何维度（路径 / IP / 状态 / 计数）」锁死，
 * 防止后续改动只挡路径、又把垃圾漏回 IP Top 或状态分布。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class NginxAccessLogParserTest {

    /**
     * 固定时间戳：可被 {@code $time_local} 解析，也用于 urlTrend 的分桶。
     */
    private static final String TIME = "27/Sep/2026:09:00:01 +0800";

    /**
     * 造一行 combined 格式日志。
     *
     * @param ip      客户端 IP
     * @param request $request 原文
     * @param status  状态码
     * @return 日志行
     */
    private static String line(String ip, String request, int status) {
        return ip + " - - [" + TIME + "] \"" + request + "\" " + status + " 512 \"-\" \"curl/8.0\"";
    }

    /**
     * 正常行：计数正确，末尾数字段归一化成 {@code :id}。
     */
    @Test
    void normalLinesCountPathsAndNormalizeIds() {
        String text = String.join("\n",
                line("192.168.1.10", "GET /api/items/123 HTTP/1.1", 200),
                line("192.168.1.10", "GET /api/items/456 HTTP/1.1", 200),
                line("192.168.1.11", "GET /index.html HTTP/1.1", 404));

        NginxAccessTopVO top = NginxAccessLogParser.parse(text, 10, null, "access.log");

        assertEquals(3, top.total());
        assertEquals(3, top.scanned());
        // 数字 ID 归一化成 :id，两条合成一条
        assertEquals(2, top.paths().size());
        assertEquals(2, top.paths().stream()
                .filter(i -> "/api/items/:id".equals(i.key())).findFirst().orElseThrow().hits());
        assertEquals(1, top.paths().stream()
                .filter(i -> "/index.html".equals(i.key())).findFirst().orElseThrow().hits());
        // 4xx 计数只来自真实行
        assertEquals(1, top.clientErrors());
    }

    /**
     * 二进制噪声行整行丢弃：路径 / IP / 状态 / 解析计数一个都不收。
     */
    @Test
    void binaryNoiseLinesAreExcludedFromEveryDimension() {
        // TLS ClientHello 片段、HTTP/2 PRI 前言、"\x00" 连串 + 随机 token，模拟公网扫描残留
        String tls = "\u0016\u0003\u0001\u00BA\u0001\u0000\u0005\u00B6\u0003\u0003G\u00A0\u00AB";
        String nulBlob = "1111\u00F8\u0000\u0000\u0000\u0004admin\u0000queryBasicCfg";
        String text = String.join("\n",
                line("10.0.0.1", tls, 400),
                line("10.0.0.2", nulBlob, 400),
                line("10.0.0.3", "PRI * HTTP/2.0", 400),
                line("10.0.0.4", "\u0000\u0000\u0008\u0004\u00D2\u0016/", 400),
                line("192.168.1.10", "GET /real HTTP/1.1", 200));

        NginxAccessTopVO top = NginxAccessLogParser.parse(text, 10, null, "access.log");

        // 5 行里 4 行是噪声：解析行数只算真实访问
        assertEquals(1, top.total());
        assertEquals(5, top.scanned());
        assertEquals(1, top.paths().size());
        assertEquals("/real", top.paths().get(0).key());
        // IP Top 与独立 IP 数同样只含真实客户端
        assertEquals(1, top.uniqueIps());
        assertTrue(top.ips().stream().allMatch(i -> "192.168.1.10".equals(i.key())),
                "IP Top 不得混入扫描器垃圾，实际=" + top.ips());
        // 噪声行的 400 状态没混进状态分布：既没有 4xx 计数，类别里也只有 2xx
        assertEquals(0, top.clientErrors());
        assertEquals(1, top.statuses().size());
        assertEquals("2xx 成功", top.statuses().get(0).category());
    }

    /**
     * 空 request（部分站点的 log_format 不记 request）：行仍计入，但没有路径可归类。
     */
    @Test
    void blankRequestLineCountsButYieldsNoPath() {
        String text = "1.2.3.4 - - [" + TIME + "] \"\" 200 512 \"-\" \"curl/8.0\"";

        NginxAccessTopVO top = NginxAccessLogParser.parse(text, 10, null, "access.log");

        assertEquals(1, top.total());
        assertTrue(top.paths().isEmpty());
        assertEquals(1, top.uniqueIps());
        assertEquals(512L, top.bytesSent());
    }

    /**
     * 绝对形式与裸协议行不是 origin-form：整行按噪声排除。
     */
    @Test
    void absoluteFormAndBareProtocolRequestsExcluded() {
        String text = String.join("\n",
                line("10.0.0.5", "http://evil.example/x", 200),
                line("10.0.0.6", "HTTP/1.1", 200),
                line("192.168.1.10", "GET /ok HTTP/1.1", 200));

        NginxAccessTopVO top = NginxAccessLogParser.parse(text, 10, null, "access.log");

        assertEquals(1, top.total());
        assertEquals(1, top.paths().size());
        assertEquals("/ok", top.paths().get(0).key());
    }

    /**
     * urlTrend 同样只保留真实路径：噪声行不进序列，正常行按命中排序。
     */
    @Test
    void urlTrendDropsNoiseAndKeepsRealPaths() {
        String text = String.join("\n",
                line("10.0.0.1", "\u0016\u0003\u0001", 400),
                line("192.168.1.10", "GET /a HTTP/1.1", 200),
                line("192.168.1.10", "GET /b?x=1 HTTP/1.1", 200));

        NginxUrlTrendVO trend = NginxAccessLogParser.urlTrend(text, 5, "access.log");

        assertEquals(2, trend.totalLines());
        List<String> paths = trend.series().stream().map(NginxUrlTrendVO.UrlSeries::path).toList();
        assertFalse(paths.contains("\u0016\u0003\u0001"), "趋势序列不得含二进制噪声: " + paths);
        assertEquals(List.of("/a", "/b"), paths);
    }
}