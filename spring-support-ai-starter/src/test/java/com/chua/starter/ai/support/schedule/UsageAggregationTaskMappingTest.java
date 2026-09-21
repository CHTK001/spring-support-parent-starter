package com.chua.starter.ai.support.schedule;

import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.entity.SysAiPricing;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.service.SysAiPricingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code UsageAggregationTask.toSysAiUsage} 落库映射测试。
 *
 * <p>解析器算出来的字段要在 {@code sys_ai_usage} 上活下来才算数：缓存命中量、总耗时、首字延迟
 * 曾因映射漏 set 而整列落空，费用兜底分支也只在外部工具不报价时才走。本用例把解析器出数与实体
 * 列逐列对齐，锁住这两处。</p>
 *
 * <p>映射方法是任务内部的私有实现，测试按反射调用，不为测试放开生产可见性。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
class UsageAggregationTaskMappingTest {

    /**
     * 定价查询入参捕获：{provider, model}
     */
    private final List<String> pricingArgs = new ArrayList<>();

    /**
     * 待返回的定价记录，为空表示查无价格
     */
    private SysAiPricing pricing;

    /**
     * 逐列搬运：Token、缓存、耗时、首字延迟、发起时刻与请求号都要落到对应列。
     */
    @Test
    @DisplayName("解析器字段逐列搬运到实体")
    void mapsEveryParsedColumn() {
        long start = Instant.parse("2026-09-20T10:00:00Z").toEpochMilli();
        AiUsage usage = AiUsage.builder()
                .provider("vscode")
                .model("gpt-5-mini")
                .requestId("chatcmpl-1")
                .inputTokens(1_000)
                .outputTokens(200)
                .totalTokens(1_200)
                .cacheTokens(800)
                .durationMillis(30_285L)
                .firstTokenLatencyMillis(22_346L)
                .startTime(start)
                .totalCost(new BigDecimal("0.5"))
                .build();

        SysAiUsage record = map("vscode-extension", usage);

        assertEquals(Integer.valueOf(1_000), record.getSysAiUsagePromptTokens());
        assertEquals(Integer.valueOf(200), record.getSysAiUsageCompletionTokens());
        assertEquals(Integer.valueOf(1_200), record.getSysAiUsageTotalTokens());
        assertEquals(Integer.valueOf(800), record.getSysAiUsageCachedTokens(), "缓存命中列不得在映射时丢掉");
        assertEquals(Long.valueOf(30_285L), record.getSysAiUsageTotalLatency(), "总耗时列不得在映射时丢掉");
        assertEquals(Long.valueOf(22_346L), record.getSysAiUsageFirstTokenLatency(), "首字延迟列不得在映射时丢掉");
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneId.systemDefault()),
                record.getSysAiUsageCallTime());
        assertEquals("chatcmpl-1", record.getSysAiUsageRequestId());
        assertEquals("gpt-5-mini", record.getSysAiUsageModel());
        assertEquals("vscode", record.getSysAiUsageProvider(), "记录自带厂商标识时不被分组名覆盖");
        assertEquals("vscode-extension", record.getSysAiUsageGroupName());
        assertEquals("external", record.getSysAiUsageCallType());
        assertEquals(0.5d, record.getSysAiUsageTotalCost(), 0d);
    }

    /**
     * 外部工具没报价时按定价表回算，并拆出输入/输出/原价。
     */
    @Test
    @DisplayName("无费用时按官方定价回算")
    void pricesFromCatalogWhenUsageHasNoCost() {
        pricing = price(2.0d, 4.0d);
        AiUsage usage = AiUsage.builder()
                .provider("opencode").model("glm-4.7").requestId("req-2")
                .inputTokens(1_000).outputTokens(500).totalTokens(1_500)
                .startTime(Instant.parse("2026-09-20T10:00:00Z").toEpochMilli())
                .build();

        SysAiUsage record = map("opencode", usage);

        assertEquals(List.of("opencode", "glm-4.7"), pricingArgs, "回算要按记录自身的厂商与模型查价");
        assertEquals(2.0d, record.getSysAiUsageInputCost(), 0d);
        assertEquals(2.0d, record.getSysAiUsageOutputCost(), 0d);
        assertEquals(4.0d, record.getSysAiUsageTotalCost(), 0d);
        assertEquals(4.0d, record.getSysAiUsageOriginalCost(), 0d);
    }

    /**
     * 定价表也没有记录时按零记账，不能留下空值让报表当缺数。
     */
    @Test
    @DisplayName("查无定价时记零费用")
    void zeroesCostWithoutPricing() {
        pricing = null;
        AiUsage usage = AiUsage.builder()
                .provider("continue").model("local-model").requestId("req-3")
                .inputTokens(10).outputTokens(5).totalTokens(15)
                .startTime(Instant.parse("2026-09-20T10:00:00Z").toEpochMilli())
                .build();

        SysAiUsage record = map("continue", usage);

        assertEquals(List.of("continue", "local-model"), pricingArgs);
        assertEquals(0.0d, record.getSysAiUsageTotalCost(), 0d);
        assertEquals(0.0d, record.getSysAiUsageInputCost(), 0d);
        assertEquals(0.0d, record.getSysAiUsageOutputCost(), 0d);
    }

    /**
     * 已带真实账单的记录不再查价；零元账单按"未报价"处理继续查价。
     */
    @Test
    @DisplayName("有账单不查价，零账单仍查价")
    void keepsReportedCostAndRepricesZeroCost() {
        SysAiUsage billed = map("claude-code", AiUsage.builder()
                .provider("claude-code").model("claude-sonnet")
                .inputTokens(10).outputTokens(5).totalTokens(15)
                .totalCost(new BigDecimal("0.25"))
                .startTime(Instant.parse("2026-09-20T10:00:00Z").toEpochMilli())
                .build());
        assertEquals(0.25d, billed.getSysAiUsageTotalCost(), 0d);
        assertEquals(List.of(), pricingArgs, "工具已出账就不该再查定价表");

        SysAiUsage zeroBilled = map("claude-code", AiUsage.builder()
                .provider("claude-code").model("claude-sonnet")
                .inputTokens(10).outputTokens(5).totalTokens(15)
                .totalCost(BigDecimal.ZERO)
                .startTime(Instant.parse("2026-09-20T10:00:00Z").toEpochMilli())
                .build());
        assertEquals(2, pricingArgs.size(), "0 元不是账单，要按定价回算");
        assertEquals(0.0d, zeroBilled.getSysAiUsageTotalCost(), 0d);
    }

    /**
     * 缺发起时刻时用当前时刻兜底，保证明细行仍能入库。
     */
    @Test
    @DisplayName("缺发起时刻仍写入调用时间")
    void fillsCallTimeWhenStartTimeMissing() {
        SysAiUsage record = map("zed", AiUsage.builder()
                .provider("zed").model("gpt-5").requestId("req-5")
                .inputTokens(1).outputTokens(1).totalTokens(2)
                .build());
        LocalDateTime callTime = record.getSysAiUsageCallTime();
        assertNull(record.getSysAiUsageCachedTokens(), "解析器没给的字段不该造值");
        assertNotNull(callTime, "明细行没有调用时间会落不进库");
        assertTrue(!callTime.isBefore(LocalDateTime.now().minusMinutes(1L))
                && !callTime.isAfter(LocalDateTime.now().plusMinutes(1L)),
                "兜底时间应取当前时刻附近，实际为 " + callTime);
    }

    /**
     * 拼一条定价记录。
     *
     * @param inputPer1k  每千输入 Token 价格
     * @param outputPer1k 每千输出 Token 价格
     * @return 定价实体
     */
    private SysAiPricing price(double inputPer1k, double outputPer1k) {
        SysAiPricing record = new SysAiPricing();
        record.setInputPricePer1k(inputPer1k);
        record.setOutputPricePer1k(outputPer1k);
        return record;
    }

    /**
     * 反射调用被测映射方法。
     *
     * @param groupName 分组名（解析器名）
     * @param usage     解析器出数
     * @return 落库实体
     */
    private SysAiUsage map(String groupName, AiUsage usage) {
        try {
            Method method = UsageAggregationTask.class
                    .getDeclaredMethod("toSysAiUsage", String.class, AiUsage.class);
            method.setAccessible(true);
            return (SysAiUsage) method.invoke(newTask(), groupName, usage);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("映射方法不可用", e);
        }
    }

    /**
     * 构造只注入定价服务的任务实例，其余协作者在本用例路径上不会被触达。
     *
     * @return 任务实例
     * @throws ReflectiveOperationException 构造或代理失败
     */
    private UsageAggregationTask newTask() throws ReflectiveOperationException {
        SysAiPricingService service = (SysAiPricingService) Proxy.newProxyInstance(
                UsageAggregationTaskMappingTest.class.getClassLoader(),
                new Class<?>[]{SysAiPricingService.class},
                (proxy, method, args) -> {
                    if ("getLatestPricing".equals(method.getName())) {
                        pricingArgs.add(String.valueOf(args[0]));
                        pricingArgs.add(String.valueOf(args[1]));
                        return pricing;
                    }
                    return null;
                });
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : UsageAggregationTask.class.getDeclaredConstructors()) {
            for (Class<?> type : candidate.getParameterTypes()) {
                if (type == SysAiPricingService.class) {
                    constructor = candidate;
                }
            }
        }
        if (constructor == null) {
            throw new AssertionError("任务构造器里找不到定价服务注入点");
        }
        constructor.setAccessible(true);
        Class<?>[] types = constructor.getParameterTypes();
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == SysAiPricingService.class) {
                values[i] = service;
            }
        }
        return (UsageAggregationTask) constructor.newInstance(values);
    }
}
