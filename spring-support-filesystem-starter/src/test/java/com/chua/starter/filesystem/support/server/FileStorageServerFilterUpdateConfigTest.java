package com.chua.starter.filesystem.support.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件服务器过滤器 —— 服务管理热更新入口 {@code updateConfig} 的行为门禁。
 *
 * <p>{@code ServerNodeRegistry#hotUpdateFilterParams}（HTTP
 * {@code POST /ms/monitor/server/{id}/filters/{i}/params}，即前端「热更新参数」按钮）
 * 直接调用本方法。本类原先<b>没有</b>覆写它，落到接口的空默认实现，于是：参数已落库、
 * 前端也弹了「已热更新」，运行中的过滤器却毫无变化，必须停→起才生效——一个纯粹的
 * 「假成功」。因此本类是否覆写、以及覆写后的落位与校验语义，都需要被测试钉住。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileStorageServerFilterUpdateConfigTest {

    /**
     * 被测对象共享的 setting
     */
    private FileStorageSetting setting;

    /**
     * 被测过滤器
     */
    private FileStorageServerFilter filter;

    @BeforeEach
    void setUp() {
        setting = FileStorageSetting.builder()
                .openPreview(true)
                .openDownload(true)
                .openRange(true)
                .openWatermark(false)
                .openWebjars(true)
                .openRemoteFile(false)
                .layout(false)
                .showFormat(true)
                .showSize(true)
                .showTime(true)
                .layoutType(FileStorageSetting.LayoutType.SIMPLE.name())
                .imageFilter("NONE")
                .watermark("原水印")
                .build();
        filter = new FileStorageServerFilter(setting);
    }

    @Test
    @DisplayName("updateConfig 必须真正落到 setting 上（这是热更新生效的前提）")
    void updateConfigAppliesToSetting() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("layout", "true");
        config.put("showFormat", "false");
        config.put("watermark", "新水印");

        filter.updateConfig(config);

        assertTrue(setting.isLayout(), "layout 应被落位为 true");
        assertFalse(setting.isShowFormat(), "showFormat 应被落位为 false");
        assertEquals("新水印", setting.getWatermark(), "watermark 应被落位");
    }

    @Test
    @DisplayName("部分更新：未传入的键保持原值，不得被重置")
    void partialUpdateKeepsUnmentionedKeys() {
        filter.updateConfig(Map.of("layout", "true"));

        assertTrue(setting.isLayout(), "本次传入的 layout 应生效");
        assertTrue(setting.isOpenPreview(), "未传入的 openPreview 应保持原值");
        assertTrue(setting.isShowSize(), "未传入的 showSize 应保持原值");
        assertEquals("原水印", setting.getWatermark(), "未传入的 watermark 应保持原值");
        assertEquals(FileStorageSetting.LayoutType.SIMPLE.name(), setting.getLayoutType(),
                "未传入的 layoutType 应保持原值");
    }

    @Test
    @DisplayName("layoutType 非法值必须抛异常，使调用方能在落库之前中止")
    void illegalLayoutTypeIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> filter.updateConfig(Map.of("layoutType", "BOGUS")));
        assertTrue(e.getMessage().contains("BOGUS"), "异常信息应回显非法值，实际: " + e.getMessage());
        assertEquals(FileStorageSetting.LayoutType.SIMPLE.name(), setting.getLayoutType(),
                "校验失败时不得改动 setting");
    }

    @Test
    @DisplayName("layoutType 大小写不敏感，且合法值被规范化后落位")
    void legalLayoutTypeIsNormalised() {
        filter.updateConfig(Map.of("layoutType", "system"));
        assertEquals(FileStorageSetting.LayoutType.SYSTEM.name(), setting.getLayoutType(),
                "合法值应按大写规范化后落位");
    }

    @Test
    @DisplayName("imageFilter 非法值必须抛异常；NONE 恒合法")
    void imageFilterIsValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> filter.updateConfig(Map.of("imageFilter", "NO_SUCH_FILTER")));
        filter.updateConfig(Map.of("imageFilter", "none"));
        assertEquals("NONE", setting.getImageFilter(), "NONE 应被接受并规范化为大写");
    }

    @Test
    @DisplayName("未知键只忽略不抛错，避免历史遗留参数让整次热更新失败")
    void unknownKeyIsIgnoredNotRejected() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("legacyKeyFromOldVersion", "whatever");
        config.put("layout", "true");

        filter.updateConfig(config);

        assertTrue(setting.isLayout(), "未知键不应影响其它键的落位");
    }

    @Test
    @DisplayName("privateBuckets / oneTimeTtlMs 属管理员能力，不在服务管理入口开放")
    void adminOnlyKeysAreNotApplied() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("privateBuckets", "a,b");
        config.put("oneTimeTtlMs", "60000");

        filter.updateConfig(config);

        assertTrue(setting.getPrivateBuckets().isEmpty(),
                "privateBuckets 不应被服务管理入口改写，实测: " + setting.getPrivateBuckets());
        assertEquals(10 * 60 * 1000L, setting.getOneTimeTtlMs(),
                "oneTimeTtlMs 不应被服务管理入口改写");
    }

    @Test
    @DisplayName("watermark 传空串等价于清除")
    void emptyWatermarkClearsIt() {
        filter.updateConfig(Map.of("watermark", ""));
        assertNull(setting.getWatermark(), "空串应清除水印");
    }

    @Test
    @DisplayName("configListener 在热更新后被回调（私有桶需同步给 OAuth 过滤器）")
    void configListenerIsNotified() {
        boolean[] called = {false};
        filter.setConfigListener(s -> called[0] = true);

        filter.updateConfig(Map.of("layout", "true"));

        assertTrue(called[0], "热更新后应触发配置回调");
    }

    @Test
    @DisplayName("openPathGuard / imageFilterEnabled 布尔开关可热更新（默认开启防穿透）")
    void newBooleansApply() {
        assertTrue(setting.isOpenPathGuard(), "路径穿透防护默认必须开启");
        assertFalse(setting.isImageFilterEnabled(), "全局滤镜链默认关闭");

        filter.updateConfig(Map.of("openPathGuard", "false", "imageFilterEnabled", "true"));

        assertFalse(setting.isOpenPathGuard(), "openPathGuard 应被落位为 false");
        assertTrue(setting.isImageFilterEnabled(), "imageFilterEnabled 应被落位为 true");
    }

    @Test
    @DisplayName("filterChain：id 大小写不敏感，规范化小写后落位，参数保留")
    void filterChainIsNormalised() {
        filter.updateConfig(Map.of("filterChain", "GRAY;Size:width=200,height=200"));

        assertEquals("gray;size:width=200,height=200", setting.getFilterChain(),
                "id 应统一小写，参数原样保留");
    }

    @Test
    @DisplayName("filterChain：非 SPI 能力 id 抛异常且不改动 setting（滤镜必须来自后台 SPI）")
    void filterChainRejectsUnknownId() {
        setting.setFilterChain("gray");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> filter.updateConfig(Map.of("filterChain", "gray;no_such_filter")));

        assertTrue(e.getMessage().contains("no_such_filter"),
                "异常信息应回显非法 id，实际: " + e.getMessage());
        assertEquals("gray", setting.getFilterChain(), "校验失败时不得改动 setting");
    }

    @Test
    @DisplayName("filterChain：空串等价于清除滤镜链")
    void filterChainBlankClears() {
        setting.setFilterChain("gray");

        filter.updateConfig(Map.of("filterChain", ""));

        assertNull(setting.getFilterChain(), "空串应清除滤镜链");
    }

    @Test
    @DisplayName("穿越检测矩阵：..、反斜杠、NUL、残留编码都识别，普通名称不误伤")
    void traversalDetectionMatrix() {
        assertNotNull(FileStorageServerFilter.findTraversal("../../etc/passwd"), ".. 段必须命中");
        assertNotNull(FileStorageServerFilter.findTraversal("a/../b"), "中间 .. 段必须命中");
        assertNotNull(FileStorageServerFilter.findTraversal("..\\..\\evil"), "反斜杠必须命中（Windows 穿越）");
        assertNotNull(FileStorageServerFilter.findTraversal("a\u0000b"), "NUL 字节必须命中");
        assertNotNull(FileStorageServerFilter.findTraversal("%2e%2e/evil"),
                "框架解码后残留的编码 .. 应再解一次识别");
        assertNotNull(FileStorageServerFilter.findTraversal("sources\r\n../../x"),
                "换行分隔的多值参数逐段判定");

        assertNull(FileStorageServerFilter.findTraversal("reports/2026/..report.pdf"), "含点文件名不得误伤");
        assertNull(FileStorageServerFilter.findTraversal("a..b/c...d"), "非独立 .. 段不得误伤");
        assertNull(FileStorageServerFilter.findTraversal("中文 目录/文件 (1).pdf"), "普通中文路径不得误伤");
        assertNull(FileStorageServerFilter.findTraversal(""), "空串视为无穿越");
    }
}
