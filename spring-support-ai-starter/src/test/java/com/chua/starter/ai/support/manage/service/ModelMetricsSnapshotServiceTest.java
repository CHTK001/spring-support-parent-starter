package com.chua.starter.ai.support.manage.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelMetricsSnapshotService#snapshotKey(String, String)} 的用例。
 *
 * <p>锁住的是「快照 Key 必须按应用隔离」这件事：原来所有内嵌本模块的应用共用一个
 * {@code nomifun:model-metrics:latest}，只挡得住空快照，挡不住「非空但属于别的应用」——
 * 同机另一个内嵌应用把自己的模型写进来，本应用读到就整段指标错位。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class ModelMetricsSnapshotServiceTest {

    @Test
    @DisplayName("应用名不同 → Key 必须不同")
    void differentAppsGetDifferentKeys() {
        String a = ModelMetricsSnapshotService.snapshotKey("ai-starter", "jdbc:mysql://127.0.0.1:3306/ai_test");
        String b = ModelMetricsSnapshotService.snapshotKey("eduai", "jdbc:mysql://127.0.0.1:3306/ai_test");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("同应用名、不同数据源 → Key 必须不同")
    void sameAppDifferentDatasourceGetsDifferentKeys() {
        String a = ModelMetricsSnapshotService.snapshotKey("ai-starter", "jdbc:mysql://127.0.0.1:3306/ai_test");
        String b = ModelMetricsSnapshotService.snapshotKey("ai-starter", "jdbc:mysql://127.0.0.1:39306/ai_test");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("同应用名、同数据源 → Key 稳定（多次调用一致）")
    void sameInputIsStable() {
        String a = ModelMetricsSnapshotService.snapshotKey("ai-starter", "jdbc:mysql://127.0.0.1:3306/ai_test");
        String b = ModelMetricsSnapshotService.snapshotKey("ai-starter", "jdbc:mysql://127.0.0.1:3306/ai_test");
        assertEquals(a, b);
    }

    @Test
    @DisplayName("应用名为空时回落缺省名，不产生空段")
    void blankAppNameFallsBack() {
        String blank = ModelMetricsSnapshotService.snapshotKey("  ", "jdbc:mysql://127.0.0.1:3306/ai_test");
        String absent = ModelMetricsSnapshotService.snapshotKey(null, "jdbc:mysql://127.0.0.1:3306/ai_test");
        String named = ModelMetricsSnapshotService.snapshotKey("ai-starter", "jdbc:mysql://127.0.0.1:3306/ai_test");
        assertEquals(absent, blank);
        assertEquals(named, blank);
        assertTrue(blank.startsWith("nomifun:model-metrics:ai-starter-"), blank);
    }

    @Test
    @DisplayName("数据源为空时不抛异常，Key 仍可用")
    void nullDatasourceIsTolerated() {
        String key = ModelMetricsSnapshotService.snapshotKey("ai-starter", null);
        assertTrue(key.startsWith("nomifun:model-metrics:ai-starter-"), key);
        assertTrue(key.endsWith(":latest"), key);
    }

    @Test
    @DisplayName("不再使用旧的全局共享 Key")
    void doesNotUseLegacySharedKey() {
        String key = ModelMetricsSnapshotService.snapshotKey("ai-starter", "jdbc:mysql://127.0.0.1:3306/ai_test");
        assertNotEquals("nomifun:model-metrics:latest", key);
    }
}
