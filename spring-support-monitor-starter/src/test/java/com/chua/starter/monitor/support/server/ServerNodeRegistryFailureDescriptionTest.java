// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点失败描述门禁。
 *
 * <p>背景是 FTPS 间歇性启动失败：日志里能看到完整的 cause 链
 * （{@code IllegalStateException: FTPS 服务器启动失败} ←
 * {@code BindException: Address already in use}，出在
 * {@code FtpsServer.doStart} 的 bind 调用），但节点 error 字段里只有外层那句
 * 「FTPS 服务器启动失败」——因为写入 error 时用的是 {@code e.getMessage()}，
 * 只取最外层包装文案。日志里有答案、界面看不到，等于没查。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("节点失败描述")
class ServerNodeRegistryFailureDescriptionTest {

    @Test
    @DisplayName("包装异常必须带出最内层根因（这正是 FTPS 那次定位不到的原因）")
    void keepsRootCauseOfWrappedException() {
        Exception wrapped = new IllegalStateException("FTPS 服务器启动失败",
                new BindException("Address already in use"));

        String text = ServerNodeRegistry.describeFailure(wrapped);

        assertTrue(text.contains("FTPS 服务器启动失败"), "应保留外层消息，实际: " + text);
        assertTrue(text.contains("Address already in use"), "应带出根因消息，实际: " + text);
        assertTrue(text.contains("BindException"), "应带出根因类型，实际: " + text);
    }

    @Test
    @DisplayName("多层嵌套也要一路下钻到最内层")
    void drillsThroughMultipleLevels() {
        Exception deep = new IllegalStateException("FTPS 服务器启动失败",
                new RuntimeException("包装层",
                        new IOException("内层根因-CM-99")));

        String text = ServerNodeRegistry.describeFailure(deep);

        assertTrue(text.contains("内层根因-CM-99"), "应取到最内层，实际: " + text);
        assertTrue(!text.contains("包装层"), "不应停在外层包装，实际: " + text);
    }

    @Test
    @DisplayName("无 cause 的异常不得凭空添加根因片段")
    void noCauseMeansNoRootCauseSuffix() {
        String text = ServerNodeRegistry.describeFailure(new IllegalStateException("纯粹失败"));

        assertEquals("纯粹失败", text, "没有 cause 时不应追加根因描述");
    }

    @Test
    @DisplayName("cause 与外层消息相同时不重复拼接")
    void noDuplicationWhenRootEqualsOuter() {
        Exception e = new IllegalStateException("同一句");

        assertEquals("同一句", ServerNodeRegistry.describeFailure(e), "不应把同一句话拼两遍");
    }

    @Test
    @DisplayName("根因无 message 时退化为类型名，不能抛 NPE")
    void rootWithoutMessageDegradesToTypeName() {
        Exception e = new IllegalStateException("外层", new UnsupportedOperationException());

        String text = ServerNodeRegistry.describeFailure(e);

        assertTrue(text.contains(UnsupportedOperationException.class.getName()),
                "无 message 的根因应退化为类型名，实际: " + text);
    }

    @Test
    @DisplayName("外层消息为 null 时仍能给出可读描述")
    void nullOuterMessageStillReadable() {
        Exception e = new RuntimeException((String) null, new BindException("端口被占"));

        String text = ServerNodeRegistry.describeFailure(e);

        assertTrue(text.contains("端口被占"), "实际: " + text);
        assertTrue(text.startsWith("null") || text.contains("根因"), "实际: " + text);
    }
}