package com.chua.starter.spider.flow;

import com.chua.starter.spider.enums.SpiderNodeType;
import com.chua.starter.spider.flow.handler.LoopNodeHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程相关常量与枚举完整性测试（不依赖 Spring 上下文）。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class SpiderFlowConstantTest {

    /**
     * SpiderNodeType 枚举完整性。
     */
    @Test
    public void testNodeTypeEnum() {
        assertEquals("start", SpiderNodeType.START.getCode());
        assertEquals("stop", SpiderNodeType.STOP.getCode());
        assertEquals("request", SpiderNodeType.REQUEST.getCode());
        assertEquals("extract", SpiderNodeType.EXTRACT.getCode());
        assertEquals("loop", SpiderNodeType.LOOP.getCode());
        assertEquals("output", SpiderNodeType.OUTPUT.getCode());
        assertEquals("script", SpiderNodeType.SCRIPT.getCode());
        assertEquals("db", SpiderNodeType.DB.getCode());
        assertEquals("sql", SpiderNodeType.SQL.getCode());
        assertEquals("function", SpiderNodeType.FUNCTION.getCode());

        // of() 安全转换
        assertEquals(SpiderNodeType.START, SpiderNodeType.of("start"));
        assertEquals(SpiderNodeType.STOP, SpiderNodeType.of(null));
        assertEquals(SpiderNodeType.START, SpiderNodeType.of("unknown"));

        // 10 个枚举全覆盖
        assertEquals(10, SpiderNodeType.values().length);
    }

    /**
     * SpiderFlowUtils 工具方法。
     */
    @Test
    public void testUtils() {
        assertEquals("fallback", SpiderFlowUtils.defaultIfEmpty(null, "fallback"));
        assertEquals("fallback", SpiderFlowUtils.defaultIfEmpty("", "fallback"));
        assertEquals("user", SpiderFlowUtils.defaultIfEmpty("user", "fallback"));
        assertEquals("", SpiderFlowUtils.defaultIfEmpty(null, ""));
    }

    /**
     * LoopNodeHandler 状态变量约束。
     */
    @Test
    public void testLoopConstants() {
        // 这些常量由 LoopNodeHandler 暴露（public static final）
        assertTrue(LoopNodeHandler.LOOP_LIST_KEY != null && !LoopNodeHandler.LOOP_LIST_KEY.isEmpty());
        assertTrue(LoopNodeHandler.LOOP_POSITION_KEY != null && !LoopNodeHandler.LOOP_POSITION_KEY.isEmpty());
    }
}
