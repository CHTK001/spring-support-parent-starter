package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.entity.MsSyncTask;
import com.chua.starter.monitor.support.model.dto.SyncTargetDTO;
import com.chua.starter.monitor.support.model.dto.SyncTaskDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同步任务「多目标保存链」的单元测试。
 *
 * <p>覆盖三件在线上最容易出错、又只有保存/回写时才走到的事：</p>
 * <ol>
 *   <li>{@code normalizeTargets}：目标缺数据源、缺表名、重复写同一张表都要在保存时拦掉；</li>
 *   <li>{@code voidChangedTargetSeqs}：只作废「增量字段变了」那个目标的水位，
 *       其它目标的水位不能被顺手清掉（否则每次改一个目标都要全量重跑）；</li>
 *   <li>{@code previousTargetIncFields}：库里存量 JSON 坏掉时回落空表，而不是把整次保存打挂。</li>
 * </ol>
 *
 * <p>这些方法都是私有的，用反射直接打；构造服务时只给 {@link ObjectMapper}，
 * 其余依赖（映射器 / 数据源中心 / 日志缓冲 / Hook 通知）在这三条路径上不参与，
 * 传 null 可以避免为了一个纯逻辑测试去搭整套 Spring 上下文。</p>
 *
 * @author CH
 */
@DisplayName("同步任务多目标保存链")
class SyncTaskMultiTargetSaveTest {

    /**
     * 被测服务：只有 objectMapper 是必需的
     */
    private final SyncTaskServiceImpl service =
            new SyncTaskServiceImpl(null, null, null, new ObjectMapper(), null, null);

    /**
     * 未配置 targets 时返回空列表，调用方继续走旧的单目标三件套。
     */
    @Test
    @DisplayName("无 targets：返回空列表，回落单目标")
    void normalizeEmpty() throws Exception {
        assertTrue(invokeTargets(request(List.of())).isEmpty());
        assertTrue(invokeTargets(request(null)).isEmpty());
    }

    /**
     * 正常的多目标：表名去空白后原样保留顺序。
     */
    @Test
    @DisplayName("多目标：按提交顺序归一化并去空白")
    void normalizeOrder() throws Exception {
        List<SyncTargetDTO> got = invokeTargets(request(List.of(
                target(20L, "  jdbc_acc.device_channel  "),
                target(21L, "ngxacc.device_channel_history"))));
        assertEquals(2, got.size());
        assertEquals("jdbc_acc.device_channel", got.get(0).targetTable());
        assertEquals(21L, got.get(1).targetDatasourceId());
    }

    /**
     * 缺数据源、缺表名都要在保存时被拒，而不是等执行才炸。
     */
    @Test
    @DisplayName("目标缺数据源 / 缺表名：保存即拒")
    void normalizeRejectsIncomplete() {
        IllegalArgumentException noDatasource = assertThrows(IllegalArgumentException.class,
                () -> invokeTargets(request(List.of(target(null, "jdbc_acc.device_channel")))));
        assertTrue(noDatasource.getMessage().contains("第 1 个目标"));
        assertTrue(noDatasource.getMessage().contains("目标数据源"));

        IllegalArgumentException noTable = assertThrows(IllegalArgumentException.class,
                () -> invokeTargets(request(List.of(target(20L, "   ")))));
        assertTrue(noTable.getMessage().contains("目标表名"));
    }

    /**
     * 同一数据源的同一张表配两次，两个目标会互相覆盖、水位也各记一份，必须拦掉。
     */
    @Test
    @DisplayName("同库同表重复：保存即拒")
    void normalizeRejectsDuplicate() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invokeTargets(request(List.of(
                        target(20L, "jdbc_acc.device_channel"),
                        target(20L, "jdbc_acc.device_channel")))));
        assertTrue(e.getMessage().contains("第 2 个目标"));
        assertTrue(e.getMessage().contains("重复"));
    }

    /**
     * 不同数据源上的同名表是合法组合，不能按表名一刀切判重复。
     */
    @Test
    @DisplayName("不同数据源的同名表：允许")
    void normalizeAllowsSameTableOtherDatasource() throws Exception {
        List<SyncTargetDTO> got = invokeTargets(request(List.of(
                target(20L, "jdbc_acc.device_channel"),
                target(21L, "jdbc_acc.device_channel"))));
        assertEquals(2, got.size());
    }

    /**
     * 只改第二个目标的增量字段：只清第二个目标的水位，第一个原样保留。
     */
    @Test
    @DisplayName("水位：只作废增量字段变化的目标")
    void voidSeqsOnlyForChangedTarget() throws Exception {
        MsSyncTask task = new MsSyncTask();
        task.setId(7L);
        Map<String, String> seqs = new HashMap<>();
        seqs.put("20#jdbc_acc.device_channel", "100");
        seqs.put("21#jdbc_acc.device_channel_history", "200");
        task.setSyncTaskTargetSeqs(new ObjectMapper().writeValueAsString(seqs));

        String previousJson = new ObjectMapper().writeValueAsString(List.of(
                target(20L, "jdbc_acc.device_channel", List.of("id")),
                target(21L, "jdbc_acc.device_channel_history", List.of("id"))));

        // 第二个目标换了增量字段，第一个没动
        List<SyncTargetDTO> now = List.of(
                target(20L, "jdbc_acc.device_channel", List.of("id")),
                target(21L, "jdbc_acc.device_channel_history", List.of("updated_at")));
        invokeVoidSeqs(task, previousJson, now);

        Map<?, ?> left = new ObjectMapper().readValue(task.getSyncTaskTargetSeqs(), Map.class);
        assertNotNull(left.get("20#jdbc_acc.device_channel"), "未变化的目标水位必须保留");
        assertNull(left.get("21#jdbc_acc.device_channel_history"), "增量字段变化的目标水位必须作废");
    }

    /**
     * 增量字段一个都没变时，水位 JSON 必须一个字都不改。
     */
    @Test
    @DisplayName("水位：字段未变则原样保留")
    void voidSeqsNoChange() throws Exception {
        MsSyncTask task = new MsSyncTask();
        task.setId(8L);
        String seqJson = "{\"20#jdbc_acc.device_channel\":\"100\"}";
        task.setSyncTaskTargetSeqs(seqJson);

        invokeVoidSeqs(task,
                new ObjectMapper().writeValueAsString(List.of(
                        target(20L, "jdbc_acc.device_channel", List.of("id")))),
                List.of(target(20L, "jdbc_acc.device_channel", List.of("id"))));

        assertEquals(seqJson, task.getSyncTaskTargetSeqs());
    }

    /**
     * 存量水位 JSON 坏掉时清空（全部目标按首跑处理），而不是让保存失败。
     */
    @Test
    @DisplayName("水位：坏 JSON 清空并继续保存")
    void voidSeqsBrokenJsonClears() throws Exception {
        MsSyncTask task = new MsSyncTask();
        task.setId(9L);
        task.setSyncTaskTargetSeqs("{not-json");

        invokeVoidSeqs(task, null, List.of(target(20L, "jdbc_acc.device_channel", List.of("id"))));

        assertNull(task.getSyncTaskTargetSeqs());
    }

    /**
     * 存量多目标配置坏掉时回落空表（于是「字段是否变过」按变化处理，水位作废），不抛异常。
     */
    @Test
    @DisplayName("存量配置坏 JSON：回落空表")
    void previousIncFieldsBrokenJson() throws Exception {
        Map<?, ?> got = invokePreviousIncFields("{oops");
        assertTrue(got.isEmpty());
        assertTrue(invokePreviousIncFields(null).isEmpty());
        assertTrue(invokePreviousIncFields("   ").isEmpty());
    }

    /**
     * 存量配置正常时，按「数据源ID#表名」取出当时的增量字段。
     */
    @Test
    @DisplayName("存量配置：按数据源#表取增量字段")
    void previousIncFieldsOk() throws Exception {
        String json = new ObjectMapper().writeValueAsString(List.of(
                target(20L, "jdbc_acc.device_channel", List.of("created_at", "id"))));
        Map<?, ?> got = invokePreviousIncFields(json);
        assertEquals(List.of("created_at", "id"), got.get("20#jdbc_acc.device_channel"));
        assertFalse(got.isEmpty());
    }

    /**
     * 增量字段就地归一化：去空白、丢空项、去重且保序。
     */
    @Test
    @DisplayName("增量字段就地归一化：去空白、丢空项、去重且保序")
    void normalizeIncFields() throws Exception {
        List<SyncTargetDTO> got = invokeTargets(request(List.of(
                new SyncTargetDTO(20L, "jdbc_acc.device_channel", "UPSERT", "INCREMENTAL",
                        java.util.Arrays.asList(" updated_at ", "", "id", "updated_at", null), null, 500, null))));
        assertEquals(List.of("updated_at", "id"), got.get(0).incFields());
    }

    /**
     * 增量字段全空（界面切到全量后留下的空串）时存 null，而不是存空数组：
     * 下游按「有没有增量字段」判断增量是否可用，空数组与 null 不该出现两种语义。
     */
    @Test
    @DisplayName("增量字段全空：归一成 null")
    void normalizeIncFieldsEmptyToNull() throws Exception {
        List<SyncTargetDTO> got = invokeTargets(request(List.of(
                new SyncTargetDTO(20L, "jdbc_acc.device_channel", "INSERT", "FULL",
                        java.util.Arrays.asList("", "  "), null, 500, null))));
        assertNull(got.get(0).incFields());
    }

    // ---------------------------------------------------------------- 反射桥

    /**
     * 调 normalizeTargets
     *
     * @param request 入参
     * @return 归一化后的目标
     */
    @SuppressWarnings("unchecked")
    private List<SyncTargetDTO> invokeTargets(SyncTaskDTO request) throws Exception {
        Method m = SyncTaskServiceImpl.class.getDeclaredMethod("normalizeTargets", SyncTaskDTO.class);
        m.setAccessible(true);
        try {
            return (List<SyncTargetDTO>) m.invoke(service, request);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 反射会把业务异常包一层：剥掉后断言才看得到 normalizeTargets 真正抛的那句中文提示
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /**
     * 调 voidChangedTargetSeqs
     *
     * @param task         任务
     * @param previousJson 存量目标 JSON
     * @param targets      本次提交的目标
     */
    private void invokeVoidSeqs(MsSyncTask task, String previousJson, List<SyncTargetDTO> targets)
            throws Exception {
        Method m = SyncTaskServiceImpl.class.getDeclaredMethod("voidChangedTargetSeqs",
                MsSyncTask.class, String.class, List.class);
        m.setAccessible(true);
        m.invoke(service, task, previousJson, new ArrayList<>(targets));
    }

    /**
     * 调 previousTargetIncFields
     *
     * @param json 存量目标 JSON
     * @return 水位键 → 增量字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> invokePreviousIncFields(String json) throws Exception {
        Method m = SyncTaskServiceImpl.class.getDeclaredMethod("previousTargetIncFields", String.class);
        m.setAccessible(true);
        return (Map<String, List<String>>) m.invoke(service, json);
    }

    // ---------------------------------------------------------------- 构造

    /**
     * 造一个只带 targets 的任务入参（其它字段在这三条路径上不参与）
     *
     * @param targets 目标列表
     * @return 入参
     */
    private static SyncTaskDTO request(List<SyncTargetDTO> targets) {
        return new SyncTaskDTO(null, null, null, null, null, null,
                null, null, targets, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 造一个目标
     *
     * @param datasourceId 目标数据源
     * @param table        目标表
     * @return 目标
     */
    private static SyncTargetDTO target(Long datasourceId, String table) {
        return target(datasourceId, table, List.of());
    }

    /**
     * 造一个带增量字段的目标
     *
     * @param datasourceId 目标数据源
     * @param table        目标表
     * @param incFields    增量字段
     * @return 目标
     */
    private static SyncTargetDTO target(Long datasourceId, String table, List<String> incFields) {
        return new SyncTargetDTO(datasourceId, table, "UPSERT", "INCREMENTAL",
                incFields, null, 500, null);
    }
}
