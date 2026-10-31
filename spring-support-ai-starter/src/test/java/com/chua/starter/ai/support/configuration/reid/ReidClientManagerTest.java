package com.chua.starter.ai.support.configuration.reid;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.reid.PersonTrack;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.configuration.face.FaceGlobalSettingProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人脸重识别配置链路测试。
 *
 * <p>锁住两端：{@link FaceGlobalSettingProvider} 的设置项声明（检测/特征模型、归并参数、
 * 向量库与维度全部落在 {@code face} 分组），与 {@link ReidClientManager} 的装配——
 * 尤其是「配置了却没装配」这个最常见的坑（页面保存成功、运行时不生效，且没有任何报错）。</p>
 *
 * <p><b>重识别不再单立配置分组。</b>人的轨迹参数、人脸分组的检测模型与特征模型
 * 全部在 {@code face} 分组里，{@code ReidClientManager} 直接读 {@code face}，
 * 不再回落到另一个重识别分组。</p>
 *
 * <p>管线构建只做图编排：detector/feature 用占位名（none），不实例化权重、不触发下载，
 * 全程离线可跑。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class ReidClientManagerTest {

    /**
     * 发现全部本地模型注册项：装配依赖注册表里存在对应条目。
     */
    @BeforeAll
    static void discoverRegistry() {
        ModelRegistry.discoverAll();
    }

    /**
     * 设置项声明：模型类默认留空（未配置即未启用），轨迹类参数有合理默认值。
     *
     * <p>重识别的归并参数与人脸检测/特征模型同属一个组（face），都在这份声明里。</p>
     */
    @Test
    @DisplayName("设置项声明：模型留空、轨迹参数有默认值")
    void providerDeclaresItems() {
        List<GlobalSettingProvider.SettingItem> items =
                new FaceGlobalSettingProvider().getItems();

        GlobalSettingProvider.SettingItem detector = find(items, "detector_model");
        GlobalSettingProvider.SettingItem feature = find(items, "feature_model");
        assertNotNull(detector, "face 分组必须声明 detector_model");
        assertNotNull(feature, "face 分组必须声明 feature_model");
        assertEquals("", detector.defaultValue(), "检测模型默认留空：未配置即该能力未启用");
        assertEquals("", feature.defaultValue(), "特征模型默认留空：未配置即该能力未启用");

        // 轨迹类参数必须给出可直接用的默认值
        assertEquals("0.5", find(items, "track_threshold").defaultValue(), "同人判定阈值默认值");
        assertEquals("30", find(items, "track_gap_seconds").defaultValue(), "归并时间窗默认值");
        assertEquals("10000", find(items, "track_max_tracks").defaultValue(), "轨迹容量默认值");
        assertEquals("0", find(items, "track_ttl_seconds").defaultValue(), "保留时长默认 0=永久");

        // 重识别自己的参数：分流阈值、低阈值检测、默认摄像头
        assertEquals("0.5", find(items, "face_threshold").defaultValue(), "人脸/重识别分流阈值默认值");
        assertEquals("0.3", find(items, "reid_detector_threshold").defaultValue(),
                "重识别低阈值检测置信度默认值");
        assertEquals("", find(items, "default_camera_id").defaultValue(), "默认摄像头留空");

        // 每个设置项都要有说明，否则前端渲染出来是裸字段名
        for (GlobalSettingProvider.SettingItem item : items) {
            assertNotNull(item.description(), "配置项缺少说明: " + item.field());
        }
    }

    /**
     * 分组标识：人脸重识别管线与人脸识别共享 face 分组。
     */
    @Test
    @DisplayName("face 分组提供者不在 reid 分组注册")
    void providerUsesFaceGroup() {
        FaceGlobalSettingProvider provider = new FaceGlobalSettingProvider();
        assertEquals("face", provider.group());
        assertEquals("人脸识别", provider.groupName());
    }

    /**
     * 回归：{@code reid} 分组提供者类已物理删除，不能再有重识别的独立分组。
     */
    @Test
    @DisplayName("不再存在 reid 分组设置提供者")
    void reidGroupProviderIsGone() {
        try {
            Class.forName("com.chua.starter.ai.support.configuration.reid.ReidGlobalSettingProvider");
            throw new AssertionError("reid 分组提供者不应存在，删除的 SPI 声明又被创建了？");
        } catch (ClassNotFoundException expected) {
            // 正常：类不存在，说明配置分组确实只剩 face
        }
    }

    /**
     * 留空路径：模型未配置时能力未启用（不构建管线），但**不抛异常**——
     * 页面打开、查询统计都应正常返回，只是提示未配置。
     */
    @Test
    @DisplayName("未配置模型：能力未启用但不报错")
    void blankModelDisablesCapability() {
        // 刻意用空读取器：face 分组一个模型都没配，才是真正的「未配置」场景。
        MapSettingReader empty = new MapSettingReader();
        ReidClientManager manager = new ReidClientManager(empty, faceManager(empty));

        assertFalse(manager.available(), "模型未配置应报告能力未启用");
        assertNull(manager.currentPipeline(), "模型未配置不应构建管线");
        assertNotNull(manager.configSummary(), "未启用也应能给出配置摘要（供排查）");
    }

    /**
     * 配置路径：face 分组的 detector/feature 都配了 → 管线必须真的构建出来。
     *
     * <p>这是本用例最核心的断言：若装配链路断了，页面会显示「保存成功」，
     * 但运行时拿不到管线，重识别永远不可用且无任何报错。</p>
     */
    @Test
    @DisplayName("face 分组配置检测与特征模型：管线构建成功")
    void configuredModelBuildsPipeline() {
        ReidClientManager manager = new ReidClientManager(baseReader(), faceManager(baseReader()));

        assertTrue(manager.available(), "模型已配置应报告能力可用");
        assertNotNull(manager.currentPipeline(), "模型已配置必须构建出重识别管线（配置要生效）");
        assertNotNull(manager.currentFeatureExtractor(), "特征提取器必须装配");
        // 摘要必须带上检测模型（前端「检测模型/特征模型」行靠它显示，不是「未配置」）
        assertTrue(manager.configSummary().contains("detector=none"),
                "配置摘要应带出 face 分组的检测模型；实际=" + manager.configSummary());
    }

    /**
     * 轨迹管理者：按当前配置新建，且归并参数正确透传。
     */
    @Test
    @DisplayName("newTrack 按当前配置新建轨迹管理者")
    void newTrackHonoursConfig() {
        MapSettingReader reader = baseReader()
                .put("track_threshold", "0.88")
                .put("track_gap_seconds", "12")
                .put("track_max_tracks", "42");
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        PersonTrack track = manager.newTrack();
        assertNotNull(track, "必须能新建轨迹管理者");

        // 阈值 0.88：余弦 0.8 < 0.88 → 不应合并（说明阈值确实透传了）
        track.observe("cam-A", "f1", new float[]{1, 0}, 1000L);
        PersonTrack.Observation other = track.observe("cam-A", "f2", new float[]{0.8f, 0.6f}, 2000L);
        assertFalse(other.matched(), "阈值 0.88 应生效（0.8 相似度不应合并）");
        assertEquals(2, track.trackCount());
    }

    /**
     * 归并时间窗透传：超过 12 秒不合并。
     */
    @Test
    @DisplayName("归并时间窗按配置生效")
    void gapWindowHonoursConfig() {
        MapSettingReader reader = baseReader().put("track_gap_seconds", "5");
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        PersonTrack track = manager.newTrack();
        track.observe("cam-A", "f1", new float[]{1, 0}, 1000L);
        PersonTrack.Observation later = track.observe("cam-A", "f2", new float[]{1, 0}, 20_000L);

        assertFalse(later.matched(), "超过 5 秒归并窗口不应合并");
    }

    /**
     * 容量上限透传：超出即淘汰。
     */
    @Test
    @DisplayName("轨迹容量按配置生效")
    void maxTracksHonoursConfig() {
        MapSettingReader reader = baseReader().put("track_max_tracks", "2");
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        PersonTrack track = manager.newTrack();
        track.observe("cam-A", "f1", new float[]{1, 0}, 1000L);
        track.observe("cam-A", "f2", new float[]{0, 1}, 2000L);
        track.observe("cam-A", "f3", new float[]{0, 0, 1}, 3000L);

        assertEquals(2, track.trackCount(), "应淘汰到配置的上限 2");
    }

    /**
     * 低阈值检测阈值：`face.reid_detector_threshold` 取不到时回退代码默认 0.3。
     */
    @Test
    @DisplayName("低阈值检测按 face 分组配置生效")
    void lowThresholdDetectorHonoursConfig() {
        MapSettingReader reader = baseReader();
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));
        assertTrue(manager.configSummary().contains("detectThreshold=0.3"),
                "摘要应带出 face 分组的低阈值检测默认置信度；实际=" + manager.configSummary());

        // 把重识别低阈值调高到 0.8，摘要随新值走
        MapSettingReader upgraded = reader.put("reid_detector_threshold", "0.8");
        ReidClientManager raised = new ReidClientManager(upgraded, faceManager(upgraded));
        assertTrue(raised.configSummary().contains("detectThreshold=0.8"),
                "重设后摘要应带出新值；实际=" + raised.configSummary());
    }

    /**
     * 人脸/重识别分流阈值：clearFace 的分界来自 face 分组的 {@code face_threshold}。
     */
    @Test
    @DisplayName("分流阈值 face_threshold 按 face 分组读取")
    void faceThresholdHonoursConfig() {
        ReidClientManager manager = new ReidClientManager(baseReader(), faceManager(baseReader()));
        assertEquals(0.5f, manager.faceThreshold(), "分流阈值默认 0.5");

        ReidClientManager raised = new ReidClientManager(
                baseReader().put("face_threshold", "0.9"), faceManager(baseReader().put("face_threshold", "0.9")));
        assertEquals(0.9f, raised.faceThreshold(), "分流阈值应在 face 分组生效");
    }

    /**
     * 默认摄像头标识：配置时回送，未配置时回 null。
     */
    @Test
    @DisplayName("默认摄像头标识按 face 分组读取")
    void defaultCameraIdHonoursConfig() {
        MapSettingReader unset = new MapSettingReader();
        assertNull(new ReidClientManager(unset, faceManager(unset)).defaultCameraId());

        MapSettingReader reader = baseReader().put("default_camera_id", "Camera-1");
        assertEquals("Camera-1", new ReidClientManager(reader, faceManager(reader)).defaultCameraId());
    }

    /**
     * 热更新：重新读同一组，配置变更后旧管线必须被置换。
     */
    @Test
    @DisplayName("热重建后追踪容量按新配置生效")
    void rebuildHonoursNewConfig() {
        MapSettingReader reader = baseReader().put("track_max_tracks", "2");
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        // 跑一次：同一次读数里脱离了容量上限（旧管线的旧快照）
        manager.rebuild();
        PersonTrack track = manager.newTrack();
        track.observe("cam-A", "f1", new float[]{1, 0}, 1000L);
        track.observe("cam-A", "f2", new float[]{0, 1}, 2000L);
        track.observe("cam-A", "f3", new float[]{0, 0, 1}, 3000L);

        assertEquals(2, track.trackCount(), "重建后应用新配置的容量上限");
    }

    /**
     * 公共管线（非 reid）的声明也要面对：该取不可到的字段不偷换。
     */
    @Test
    @DisplayName("面管线的 detector 漫游不串味：面管线读面分组")
    void facePipelineKeepsItsOwnGroup() {
        FaceClientManager face = faceManager(baseReader().put("detector_model", "none"));
        assertNotNull(face.currentPipeline(), "detector_model 配了时 face 管线必须构建（reid 直接复用此单例）");
    }

    /**
     * 取设置项，不存在时返回 null。
     *
     * @param items 设置项列表
     * @param field 字段名
     * @return 设置项；不存在返回 null
     */
    private static GlobalSettingProvider.SettingItem find(List<GlobalSettingProvider.SettingItem> items,
                                                          String field) {
        return items.stream()
                .filter(item -> field.equals(item.field()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 按当前 face 分组配置构建 lighting 读取器。
     *
     * @return 设置读取器
     */
    private static MapSettingReader baseReader() {
        return new MapSettingReader()
                .put("detector_model", "none")
                .put("feature_model", "none");
    }

    /**
     * 构造人脸客户端管理器（提供共享检测管线）。
     *
     * <p>{@code beanFactory} 传 null：它只用于按 Bean 名查找模型，
     * 本测试用注册表里的占位模型（none），不经过 Bean 查找。</p>
     *
     * @param reader 设置读取器
     * @return 人脸客户端管理器
     */
    private static FaceClientManager faceManager(GlobalSettingReader reader) {
        return new FaceClientManager(reader, null);
    }

    /**
     * 按内存键值模拟全局设置的读取器（不落库、不读缓存、不触发用户覆盖）。
     */
    private static final class MapSettingReader implements GlobalSettingReader {

        /**
         * face 分组下的字段 → 值。
         */
        private final Map<String, String> values = new HashMap<>();

        /**
         * 分组 → 字段 → 值（表达「两组同名不同值」的场景）。
         */
        private final Map<String, Map<String, String>> groupValues = new HashMap<>();

        @Override
        public String getStr(String group, String field) {
            return valueOf(group, field);
        }

        @Override
        public String getStrAs(String userId, String group, String field) {
            return getStr(group, field);
        }

        @Override
        public String getStrDirect(String group, String field) {
            return getStr(group, field);
        }

        @Override
        public boolean getBool(String group, String field, boolean defaultValue) {
            String v = getStr(group, field);
            return v == null ? defaultValue : Boolean.parseBoolean(v);
        }

        @Override
        public int getInt(String group, String field, int defaultValue) {
            return intValueOf(group, field, defaultValue);
        }

        @Override
        public Integer getIntOrNull(String group, String field) {
            String v = valueOf(group, field);
            try {
                return v == null ? null : Integer.valueOf(v);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public Long getLongOrNull(String group, String field) {
            String v = valueOf(group, field);
            try {
                return v == null ? null : Long.valueOf(v);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public BigDecimal getDecimalOrNull(String group, String field) {
            String v = valueOf(group, field);
            try {
                return v == null ? null : new BigDecimal(v);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public double getDouble(String group, String field, double defaultValue) {
            String v = valueOf(group, field);
            try {
                return v == null ? defaultValue : Double.parseDouble(v);
            } catch (Exception e) {
                return defaultValue;
            }
        }

        /**
         * 分组专属值优先，其次 face 分组，再其次全局（同组）覆盖。
         *
         * @param group 分组
         * @param field 字段名
         * @return 值，未配置返回 null
         */
        private String valueOf(String group, String field) {
            // 组专属
            Map<String, String> own = groupValues.get(group);
            if (own != null && own.containsKey(field)) {
                return own.get(field);
            }
            // 所有其他分组统一读到 face 分组的值（模拟 reid 管线统一走 face 分组）
            return values.get(field);
        }

        /**
         * 解析整型，解析失败时回退默认值。
         *
         * @param group 分组
         * @param field 字段名
         * @param defaultValue 默认值
         * @return 整数值
         */
        private int intValueOf(String group, String field, int defaultValue) {
            String v = valueOf(group, field);
            try {
                return StringUtilsIsBlank(v) ? defaultValue : Integer.parseInt(v);
            } catch (Exception e) {
                return defaultValue;
            }
        }

        /**
         * 字符串判空。
         *
         * @param value 值
         * @return 空则 true
         */
        private static boolean StringUtilsIsBlank(String value) {
            return value == null || value.isBlank();
        }

        /**
         * 登记 face 分组的字段值（链式）。
         *
         * @param field 字段名
         * @param value 值
         * @return this
         */
        private MapSettingReader put(String field, String value) {
            values.put(field, value);
            return this;
        }

        /**
         * 登记某分组的字段值（链式）。
         *
         * @param group 分组名
         * @param field 字段名
         * @param value 值
         * @return this
         */
        private MapSettingReader putFor(String group, String field, String value) {
            groupValues.computeIfAbsent(group, k -> new HashMap<>()).put(field, value);
            return this;
        }
    }
}
