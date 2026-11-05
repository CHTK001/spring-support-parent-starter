// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.toolmetrics;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@code LocalToolUsageSyncService.insertOne} 落库映射测试。
 *
 * <p>本地工具走的不是聚合任务那条路，字段搬运是另一份代码：缓存命中量、总耗时、首字延迟
 * 曾在这一侧同样漏 set；去重键又要稳定（同一条记录重复同步不能变成两行）。本用例用一张假表
 * 把这两点钉住。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
class LocalToolUsageSyncServiceTest {

    /**
     * 某条记录已存在时的计数值
     */
    private static final long ALREADY_EXISTS = 1L;

    /**
     * 落库调用时间基准
     */
    private static final long CALL_MILLIS = Instant.parse("2026-09-20T10:00:00Z").toEpochMilli();

    /**
     * 捕获到的插入行
     */
    private final List<SysAiUsage> inserted = new ArrayList<>();

    /**
     * 假表返回的存在性计数
     */
    private long existingCount;

    /**
     * 初始化 MyBatis-Plus 的 lambda 列缓存，使去重查询能在无 Spring 容器下构造条件包装器。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("sysAiUsageMapper");
        TableInfoHelper.initTableInfo(assistant, SysAiUsage.class);
    }

    /**
     * 每条用例重置假表状态
     */
    @BeforeEach
    void resetProbe() {
        inserted.clear();
        existingCount = 0L;
    }

    /**
     * 逐列搬运：解析器的 Token、缓存、耗时、首字延迟都要出现在插入行上。
     */
    @Test
    @DisplayName("本地工具记录逐列搬运")
    void writesCacheAndLatencyColumns() {
        boolean added = insert("cline", AiUsage.builder()
                .provider("cline").model("claude-sonnet")
                .inputTokens(1_000).outputTokens(200).totalTokens(1_200)
                .cacheTokens(800).durationMillis(30_285L).firstTokenLatencyMillis(22_346L)
                .startTime(CALL_MILLIS)
                .build());

        assertTrue(added);
        SysAiUsage row = onlyRow();
        assertEquals("local", row.getSysAiUsageGroupName());
        assertEquals("local-tool", row.getSysAiUsageCallType());
        assertEquals("cline", row.getSysAiUsageProvider());
        assertEquals("claude-sonnet", row.getSysAiUsageModel());
        assertEquals(Integer.valueOf(1_000), row.getSysAiUsagePromptTokens());
        assertEquals(Integer.valueOf(200), row.getSysAiUsageCompletionTokens());
        assertEquals(Integer.valueOf(1_200), row.getSysAiUsageTotalTokens());
        assertEquals(Integer.valueOf(800), row.getSysAiUsageCachedTokens(), "缓存列不得丢掉");
        assertEquals(Long.valueOf(30_285L), row.getSysAiUsageTotalLatency(), "总耗时列不得丢掉");
        assertEquals(Long.valueOf(22_346L), row.getSysAiUsageFirstTokenLatency(), "首字延迟列不得丢掉");
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(CALL_MILLIS), ZoneId.systemDefault()),
                row.getSysAiUsageCallTime());
        assertTrue(row.getSysAiUsageRequestId().startsWith("cline:"),
                "去重键要带解析器前缀: " + row.getSysAiUsageRequestId());
    }

    /**
     * 同一记录再次同步时按去重键跳过，不产生第二行。
     */
    @Test
    @DisplayName("已存在的请求号不再插入")
    void skipsAlreadyInsertedRequest() {
        existingCount = ALREADY_EXISTS;

        assertFalse(insert("zcode", record("m1", CALL_MILLIS)));
        assertTrue(inserted.isEmpty(), "命中去重后不该再插入");
    }

    /**
     * 去重键只由取数维度决定：同内容稳定、换内容不同。
     */
    @Test
    @DisplayName("去重键稳定且可区分")
    void dedupKeyIsStableAndDistinguishing() {
        insert("kilo", record("glm-4.7", CALL_MILLIS));
        insert("kilo", record("glm-4.7", CALL_MILLIS));
        insert("kilo", record("glm-4.7", CALL_MILLIS + 1_000L));
        insert("other", record("glm-4.7", CALL_MILLIS));

        List<String> keys = inserted.stream().map(SysAiUsage::getSysAiUsageRequestId).toList();
        assertEquals(keys.get(0), keys.get(1), "同一记录重复同步必须撞同一个键");
        assertFalse(keys.get(0).equals(keys.get(2)), "换发起时刻就是另一次请求");
        assertFalse(keys.get(0).equals(keys.get(3)), "换解析器就是另一个数据源");
    }

    /**
     * 总价缺失时由输入加输出费用合成；两侧都没有则留空，不写 0 混淆免费。
     */
    @Test
    @DisplayName("总价按分段费用合成")
    void sumsSegmentCosts() {
        insert("pi", AiUsage.builder().provider("pi").model("m")
                .inputTokens(10).outputTokens(5).totalTokens(15).startTime(CALL_MILLIS)
                .inputCost(new BigDecimal("0.1")).outputCost(new BigDecimal("0.2")).build());
        insert("pi", AiUsage.builder().provider("pi").model("m2")
                .inputTokens(10).outputTokens(5).totalTokens(15).startTime(CALL_MILLIS)
                .totalCost(new BigDecimal("0.7")).build());
        insert("pi", AiUsage.builder().provider("pi").model("m3")
                .inputTokens(10).outputTokens(5).totalTokens(15).startTime(CALL_MILLIS).build());

        assertEquals(0.3d, inserted.get(0).getSysAiUsageTotalCost(), 1e-9);
        assertEquals(0.7d, inserted.get(1).getSysAiUsageTotalCost(), 1e-9);
        assertNull(inserted.get(2).getSysAiUsageTotalCost(), "分文未记与零元账单不能混为一谈");
    }

    /**
     * 解析器没给厂商标识时退回解析器名；没给发起时刻时不造假时刻。
     */
    @Test
    @DisplayName("缺厂商标识时退回解析器名")
    void fallsBackToParserName() {
        insert("grok", AiUsage.builder().model("grok-code")
                .inputTokens(1).outputTokens(1).totalTokens(2).build());

        SysAiUsage row = onlyRow();
        assertEquals("grok", row.getSysAiUsageProvider());
        assertNull(row.getSysAiUsageCallTime(), "没有发起时刻就不造假时刻");
    }

    /**
     * 拼一条只有取数维度的记录。
     *
     * @param model     模型标识
     * @param startTime 发起时刻
     * @return 用量记录
     */
    private static AiUsage record(String model, long startTime) {
        return AiUsage.builder()
                .provider("kilo").model(model)
                .inputTokens(10).outputTokens(5).totalTokens(15)
                .startTime(startTime)
                .build();
    }

    /**
     * 取唯一插入行。
     *
     * @return 插入行
     */
    private SysAiUsage onlyRow() {
        assertEquals(1, inserted.size(), "期望且仅期望一次插入");
        return inserted.getFirst();
    }

    /**
     * 反射调用被测落库方法。
     *
     * @param source 解析器名
     * @param usage  用量记录
     * @return 是否新增写入
     */
    private boolean insert(String source, AiUsage usage) {
        try {
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : LocalToolUsageSyncService.class.getDeclaredConstructors()) {
                if (candidate.getParameterTypes().length == 1
                        && candidate.getParameterTypes()[0] == SysAiUsageMapper.class) {
                    constructor = candidate;
                }
            }
            if (constructor == null) {
                fail("未找到注入 SysAiUsageMapper 的构造器");
            }
            constructor.setAccessible(true);
            Object service = constructor.newInstance(mapper());
            Method method = LocalToolUsageSyncService.class
                    .getDeclaredMethod("insertOne", String.class, AiUsage.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(service, source, usage);
        } catch (IllegalAccessException | InstantiationException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new AssertionError("落库方法不可反射调用", e);
        }
    }

    /**
     * 造一张假表：记录插入行并按用例设定返回存在性计数。
     *
     * @return 映射器代理
     */
    private SysAiUsageMapper mapper() {
        return (SysAiUsageMapper) Proxy.newProxyInstance(
                LocalToolUsageSyncServiceTest.class.getClassLoader(),
                new Class<?>[]{SysAiUsageMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        inserted.add((SysAiUsage) args[0]);
                        return 1;
                    }
                    if ("selectCount".equals(method.getName())) {
                        return existingCount;
                    }
                    return null;
                });
    }
}