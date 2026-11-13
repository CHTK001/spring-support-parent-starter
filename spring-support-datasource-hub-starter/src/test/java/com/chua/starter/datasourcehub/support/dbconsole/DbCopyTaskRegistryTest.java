package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 复制任务进度注册表的收尾语义。
 *
 * <p><b>为什么要测</b>：数据段每 500 行推一次「已复制 N / M 行」，只更新顶层描述；
 * 任务在数据段被取消时，步骤副标题会停留在启动时的「统计源表行数」，
 * 弹窗里「复制表数据」这一步显示「统计源表行数」、顶层却写「已取消复制」，
 * 两个字段自相矛盾。这里钉死「步骤副标题跟到最新进度」这条行为。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class DbCopyTaskRegistryTest {

    private DbCopyTaskRegistry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    /**
     * 建一个三步任务：结构 → 建表 → 数据。
     *
     * @return 任务 id
     */
    private String newTask() {
        registry = new DbCopyTaskRegistry(new ObjectMapper());
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("structure", "读取源表结构");
        steps.put("create", "创建目标表");
        steps.put("data", "复制表数据");
        String taskId = registry.create("复制表", "dbA.t_big → dbB.t_big", steps);
        registry.startStep(taskId, "structure", "读取 t_big 的建表语句");
        registry.finishStep(taskId, "structure", "SUCCESS", "建表语句 264 字节");
        registry.startStep(taskId, "create", "检查目标表是否同名");
        registry.finishStep(taskId, "create", "SUCCESS", "已创建目标表");
        registry.startStep(taskId, "data", "统计源表行数");
        return taskId;
    }

    /**
     * 取快照（任务列表只有这一个任务）。
     *
     * @return 快照
     */
    private Map<String, Object> snapshot() {
        List<Map<String, Object>> list = registry.snapshots();
        assertEquals(1, list.size(), "任务列表应只有一个任务");
        return list.get(0);
    }

    /**
     * 取某一步的补充说明。
     *
     * @param snapshot 快照
     * @param stepKey  步骤键
     * @return detail
     */
    @SuppressWarnings("unchecked")
    private String stepDetail(Map<String, Object> snapshot, String stepKey) {
        for (Map<String, Object> step : (List<Map<String, Object>>) snapshot.get("steps")) {
            if (stepKey.equals(step.get("key"))) {
                return (String) step.get("detail");
            }
        }
        return null;
    }

    @Test
    @DisplayName("数据段中途取消：步骤副标题保留已写入行数，而不是「统计源表行数」")
    void cancelledKeepsLatestDataProgress() {
        String taskId = newTask();
        // [P3C A20 豁免]
        registry.progress(taskId, 81, "已复制 233500 / 300000 行");
        registry.cancel(taskId);
        registry.finish(taskId, "CANCELLED", "已取消复制");

        Map<String, Object> snap = snapshot();
        assertEquals("CANCELLED", snap.get("status"));
        assertEquals("已取消复制", snap.get("currentDescription"));
        assertEquals("已复制 233500 / 300000 行", stepDetail(snap, "data"));
        assertEquals("CANCELLED", stepDetail(snap, "data") == null ? null : stepStatus(snap, "data"));
    }

    @Test
    @DisplayName("数据段成功后收尾：副标题是最终写入行数，不是过程文案")
    void successOverridesProcessDetail() {
        String taskId = newTask();
        // [P3C A20 豁免]
        registry.progress(taskId, 60, "已复制 150000 / 300000 行");
        registry.finishStep(taskId, "data", "SUCCESS", "已写入 300000 行");
        // [P3C A20 豁免]
        registry.progress(taskId, 95, "数据复制完成");
        registry.finish(taskId, "SUCCESS", "已复制到 dbB.t_big");

        Map<String, Object> snap = snapshot();
        assertEquals("SUCCESS", snap.get("status"));
        // [P3C A20 豁免]
        assertEquals(100, ((Number) snap.get("percentage")).intValue());
        assertEquals("已写入 300000 行", stepDetail(snap, "data"));
    }

    @Test
    @DisplayName("数据段失败：副标题保留失败前的最后进度")
    void failedKeepsLatestDataProgress() {
        String taskId = newTask();
        // [P3C A20 豁免]
        registry.progress(taskId, 70, "已复制 200000 / 300000 行");
        registry.finish(taskId, "FAILED", "复制表失败: 连接中断");

        Map<String, Object> snap = snapshot();
        assertEquals("FAILED", snap.get("status"));
        assertEquals("已复制 200000 / 300000 行", stepDetail(snap, "data"));
    }

    /**
     * 取某一步的状态。
     *
     * @param snapshot 快照
     * @param stepKey  步骤键
     * @return 状态
     */
    @SuppressWarnings("unchecked")
    private String stepStatus(Map<String, Object> snapshot, String stepKey) {
        for (Map<String, Object> step : (List<Map<String, Object>>) snapshot.get("steps")) {
            if (stepKey.equals(step.get("key"))) {
                return (String) step.get("status");
            }
        }
        return null;
    }
}
