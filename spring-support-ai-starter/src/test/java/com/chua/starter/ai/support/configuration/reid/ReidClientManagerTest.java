package com.chua.starter.ai.support.configuration.reid;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.reid.PersonTrack;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * <p>锁住两端：{@link com.chua.starter.ai.support.configuration.face.FaceGlobalSettingProvider}
 * 的设置项声明（reid 与 face 共用 face 分组，不再有独立的 reid 分组），与
 * {@link ReidClientManager} 的装配——尤其是「配置了却没装配」这个最常见的坑
 * （页面保存成功、运行时不生效，且没有任何报错）。</p>
 *
 * <p>管线构建只做图编排：detector/feature 用占位名（none），
 * 不实例化权重、不触发下载，全程离线可跑。</p>
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
     */
    @Test
    @DisplayName("设置项声明：模型留空、轨迹参数有默认值")
    void providerDeclaresItems() {
        List<GlobalSettingProvider.SettingItem> items =
                new com.chua.starter.ai.support.configuration.face.FaceGlobalSettingProvider().getItems();

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

        // 每个设置项都要有说明，否则前端渲染出来是裸字段名
        for (GlobalSettingProvider.SettingItem item : items) {
            assertNotNull(item.description(), "配置项缺少说明: " + item.field());
        }
    }

    /**
     * 分组标识：reid 已并入 face 分组（配置分组与前端/后端约定一致）。
     */
    @Test
    @DisplayName("配置分组为 face")
    void providerUsesFaceGroup() {
        com.chua.starter.ai.support.configuration.face.FaceGlobalSettingProvider provider =
                new com.chua.starter.ai.support.configuration.face.FaceGlobalSettingProvider();
        assertEquals("face", provider.group());
        assertEquals("人脸识别", provider.groupName());
    }

    /**
     * 留空路径：模型未配置时能力未启用（不构建管线），但**不抛异常**——
     * 页面打开、查询统计都应正常返回，只是提示未配置。
     */
    @Test
    @DisplayName("未配置模型：能力未启用但不报错")
    void blankModelDisablesCapability() {
        // 刻意用空读取器：模型一个都没配，才是真正的「未配置」场景。
        // （用 baseReader 的话 reid 会回落到 face 分组，能力其实可用，断言就不成立了）
        MapSettingReader empty = new MapSettingReader();
        ReidClientManager manager = new ReidClientManager(empty, faceManager(empty));

        assertFalse(manager.available(), "模型未配置应报告能力未启用");
        assertNull(manager.currentPipeline(), "模型未配置不应构建管线");
        assertNotNull(manager.configSummary(), "未启用也应能给出配置摘要（供排查）");
    }

    /**
     * 配置路径：detector/feature 都配了 → 管线必须真的构建出来。
     *
     * <p>这是本用例最核心的断言：若装配链路断了，页面会显示「保存成功」，
     * 但运行时拿不到管线，重识别永远不可用且无任何报错。</p>
     */
    @Test
    @DisplayName("配置检测与特征模型：管线构建成功")
    void configuredModelBuildsPipeline() {
        ReidClientManager manager = new ReidClientManager(baseReader(), faceManager(baseReader()));

        assertTrue(manager.available(), "模型已配置应报告能力可用");
        assertNotNull(manager.currentPipeline(), "模型已配置必须构建出重识别管线（配置要生效）");
        assertNotNull(manager.currentFeatureExtractor(), "特征提取器必须装配");
    }

    /**
     * 轨迹管理器：按当前配置新建，且归并参数正确透传。
     */
    @Test
    @DisplayName("newTrack 按当前配置新建轨迹管理器")
    void newTrackHonoursConfig() {
        MapSettingReader reader = baseReader()
                .put("track_threshold", "0.88")
                .put("track_gap_seconds", "12")
                .put("track_max_tracks", "42");
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        PersonTrack track = manager.newTrack();
        assertNotNull(track, "必须能新建轨迹管理器");

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
     * 默认摄像头标识：未配置返回空。
     */
    @Test
    @DisplayName("默认摄像头未配置时返回空")
    void defaultCameraIdDefaultsToNull() {
        ReidClientManager manager = new ReidClientManager(baseReader(), faceManager(baseReader()));
        assertNull(manager.defaultCameraId());
    }

    /**
     * 回落语义：reid 分组留空时回落到 face 分组（两者用同一批模型，用户只需配一次）。
     */
    @Test
    @DisplayName("reid 分组留空：回落到 face 分组的模型")
    void fallsBackToFaceGroupModels() {
        // face 分组配了模型、reid 分组留空
        MapSettingReader reader = faceOnlyReader();
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        assertTrue(manager.available(), "face 分组配了模型时，reid 应回落可用（不必配两遍）");
        assertNotNull(manager.currentPipeline());
    }

    /**
     * 反向兜底：face 分组没配检测模型、但 reid 分组自己配了 → 能力必须可用。
     *
     * <p>这是部署实测踩到的坑：重识别配置页提供了「检测模型」下拉，用户选了它、
     * 保存成功、页面显示已配置，但能力始终 available=false —— 因为检测管线取的是
     * face 分组的共享单例。用户得额外再去配一遍人脸识别分组才能用，而界面上
     * 完全看不出这层依赖。</p>
     */
    @Test
    @DisplayName("face 分组未配检测模型：用 reid 分组自己的 detector_model 兜底")
    void buildsOwnDetectorWhenFaceGroupUnset() {
        MapSettingReader reader = new MapSettingReader()
                .putFor("reid", "detector_model", "none")
                .putFor("reid", "feature_model", "none");
        // faceManager 复用同一个 reader：face 分组读不到 detector_model，其管线为 null
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        assertNull(faceManager(reader).currentPipeline(),
                "前提：face 分组不应构建出检测管线（本用例要验证的是 reid 自建）");
        assertTrue(manager.available(), "reid 分组自己配了检测模型时应可用");
        assertNotNull(manager.currentPipeline(), "必须用 reid 分组的 detector_model 构建出管线");
    }

    /**
     * 两边都没配 → 依然不可用（不因新增兜底逻辑而误报可用）。
     */
    @Test
    @DisplayName("两个分组都没配检测模型：保持不可用")
    void staysUnavailableWhenNoDetectorAnywhere() {
        MapSettingReader reader = new MapSettingReader();
        ReidClientManager manager = new ReidClientManager(reader, faceManager(reader));

        assertFalse(manager.available(), "无任何检测模型配置时应保持不可用");
        assertNull(manager.currentPipeline());
    }

    /**
     * 构造只读 face 分组的读取器（reid 分组留空）。
     *
     * @return 设置读取器
     */
    private static MapSettingReader faceOnlyReader() {
        MapSettingReader reader = new MapSettingReader();
        reader.values.put("detector_model", "none");
        reader.values.put("feature_model", "none");
        reader.faceOnly = true;
        return reader;
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
     * 构造 reid 分组设置：检测与特征模型都用占位名 none。
     *
     * <p>{@code none} 是注册表里的占位模型：能建出管线但不加载权重，
     * 因此测试全程离线。</p>
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
    private static com.chua.starter.ai.support.configuration.face.FaceClientManager faceManager(
            GlobalSettingReader reader) {
        return new com.chua.starter.ai.support.configuration.face.FaceClientManager(reader, null);
    }

    /**
     * 按内存键值模拟全局设置的读取器（不落库、不读缓存、不触发用户覆盖）。
     */
    private static final class MapSettingReader extends GlobalSettingReader {

        /**
         * 字段 → 值。
         */
        private final Map<String, String> values = new HashMap<>();

        /**
         * 只在 face 分组返回取值（模拟「reid 分组留空、回落 face」）。
         */
        private boolean faceOnly;

        /**
         * 分组 → (字段 → 值)。用于表达「两个分组的键同名但值不同」的场景，
         * 例如「face 分组没配检测模型、reid 分组配了」——用单一 {@link #values}
         * 表达不了这种差异。
         */
        private final Map<String, Map<String, String>> groupValues = new HashMap<>();

        private MapSettingReader() {
            // 第 4 参 = YAML 覆盖层：双轨无 yaml 语义，传 null
            super(null, null, null, null);
        }

        /**
         * 登记一个字段值（链式）。
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
         * 登记某分组下的字段值（链式）。
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

        @Override
        public String getStr(String group, String field) {
            // 分组专属值优先：它表达的是「该分组确实配了这个键」
            Map<String, String> own = groupValues.get(group);
            if (own != null && own.containsKey(field)) {
                return own.get(field);
            }
            if (faceOnly) {
                // 只在 face 分组取值：reid 分组读不到 → 触发后端的回落逻辑
                return "face".equals(group) ? values.get(field) : null;
            }
            // reid 分组优先，其次回落 face 分组（两者用同一批模型）。
            // values 为空时两个分组都取不到 —— 这才是真正的「模型未配置」场景。
            String value = "reid".equals(group) ? values.get(field) : null;
            if (value == null && "face".equals(group)) {
                value = values.get(field);
            }
            return value;
        }
    }
}
