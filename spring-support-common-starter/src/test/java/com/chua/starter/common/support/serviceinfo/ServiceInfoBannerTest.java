package com.chua.starter.common.support.serviceinfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ServiceInfoBanner} 布局测试。
 *
 * <p>核心断言: 横幅每一行的"终端可见列宽"完全相等 —— 这是右边界不错位的充要条件。
 * 宽度计算刻意使用测试内独立实现, 不复用被测类的算法, 避免"自证清白"。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
class ServiceInfoBannerTest {

    /** 内容宽度上限, 与 ServiceInfoBanner.MAX_INNER 保持一致 */
    private static final int MAX_INNER = 118;

    /** 合法的行首 -> 行尾框线字符 */
    private static final Map<Character, Character> FRAME_PAIRS = Map.of(
            '║', '║', '╟', '╢', '╔', '╗', '╠', '╣', '╚', '╝');

    private ServiceInfoRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ServiceInfoRegistry.getInstance();
        registry.reset();
    }

    @Test
    @DisplayName("空注册表不输出横幅")
    void renderEmptyRegistryReturnsEmptyString() {
        assertThat(ServiceInfoBanner.render(registry)).isEmpty();
    }

    @Test
    @DisplayName("所有行可见列宽相等, 且以框线字符起止")
    void allRowsShareSameVisibleWidth() {
        registerTypical();

        List<String> rows = rowsOf(ServiceInfoBanner.render(registry));

        assertThat(rows).isNotEmpty();
        assertThat(rows.stream().mapToInt(ServiceInfoBannerTest::visibleWidth).distinct())
                .as("每行的可见列宽必须完全一致").hasSize(1);
        assertThat(rows).allSatisfy(row -> {
            String plain = row.replaceAll("\u001B\\[[0-9;]*m", "");
            // 内容行 ║…║、分组分隔行 ╟…╢、外框行 ╔…╗ / ╠…╣ / ╚…╝
            Character expectedEnd = FRAME_PAIRS.get(plain.charAt(0));
            assertThat(expectedEnd).as("未知的行首框线字符: %s", plain.charAt(0)).isNotNull();
            assertThat(plain.charAt(plain.length() - 1)).isEqualTo(expectedEnd);
        });
    }

    @Test
    @DisplayName("CJK 与全角字符按 2 列计算宽度")
    void cjkCountsAsTwoColumns() {
        registry.registerFlag("cjk", "中文分组", true, "数据库只读模式 (拒绝写操作)");

        List<String> rows = rowsOf(ServiceInfoBanner.render(registry));

        assertThat(rows.stream().mapToInt(ServiceInfoBannerTest::visibleWidth).distinct()).hasSize(1);
        assertThat(rows.stream().anyMatch(row -> row.contains("数据库只读模式 (拒绝写操作)"))).isTrue();
    }

    @Test
    @DisplayName("端点按 URL 路径去重, 保留 URL 更完整的一条")
    void duplicateEndpointsAreCollapsed() {
        registry.registerEndpoint("文档", "接口文档", "/doc.html", "接口文档");
        registry.registerEndpoint("文档", "swagger", "http://127.0.0.1:8787/doc.html", "Knife4j 接口文档");
        registry.registerEndpoint("监控", "actuator", "http://127.0.0.1:8787/actuator", "Spring Boot Actuator");

        String banner = ServiceInfoBanner.render(registry);

        assertThat(countOccurrences(banner, "/doc.html")).as("同一路径只应出现一次").isEqualTo(1);
        assertThat(banner).contains("文档/swagger")
                .contains("http://127.0.0.1:8787/doc.html")
                .doesNotContain("文档/接口文档");
    }

    @Test
    @DisplayName("开关按 分组+显示名+取值 去重, 取值不同则保留以暴露冲突")
    void duplicateFlagsAreCollapsed() {
        registry.registerFlag("knife4j", "文档", true, "Knife4j接口文档");
        registry.registerFlag("swagger", "文档", true, "Knife4j 接口文档");
        registry.registerFlag("conflict", "文档", false, "Knife4j 接口文档");

        String banner = ServiceInfoBanner.render(registry);

        assertThat(countOccurrences(banner, "[开启]")).isEqualTo(1);
        assertThat(countOccurrences(banner, "[关闭]")).isEqualTo(1);
    }

    @Test
    @DisplayName("三态开关: 未配置显示为灰色未配置")
    void triStateFlagRendersUnset() {
        registry.registerFlag("unset", "边界", null, "未配置的开关");

        assertThat(ServiceInfoBanner.render(registry)).contains("[未配置]");
    }

    @Test
    @DisplayName("超长内容被截断, 仍保持等宽且不超上限")
    void overLongContentIsTruncatedAndStillAligned() {
        registry.registerFlag("long", "边界", true, "超长描述".repeat(40), "[平台: system]");

        List<String> rows = rowsOf(ServiceInfoBanner.render(registry));

        assertThat(rows.stream().mapToInt(ServiceInfoBannerTest::visibleWidth).distinct()).hasSize(1);
        assertThat(rows.stream().mapToInt(ServiceInfoBannerTest::visibleWidth).max().orElseThrow())
                .isLessThanOrEqualTo(MAX_INNER + 2);
        assertThat(String.join("\n", rows)).contains("...");
    }

    // ------------------------------------------------------------------ 辅助

    /** 注册一份贴近真实启动场景的数据 */
    private void registerTypical() {
        registry.registerEndpoint("AI", "AI接口", "/ai/**", "AI接口");
        registry.registerEndpoint("通用", "应用自身", "http://127.0.0.1:8787/service-info", "服务信息聚合端点");
        registry.registerFlag("cors", "API控制", true, "跨域处理");
        registry.registerFlag("actuatorAuth", "监控", false, "Actuator 认证过滤器");
        registry.registerFlag("readOnly", "MyBatis", false, "数据库只读模式 (拒绝写操作)");
        registry.registerFlag("unset", "MyBatis", null, "数据权限拦截");
    }

    /** 拆行并去掉空行 */
    private static List<String> rowsOf(String banner) {
        List<String> rows = new ArrayList<>();
        for (String row : banner.split("\n", -1)) {
            if (!row.trim().isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }

    // ------------------------------------------------------------------ 独立的宽度实现

    /** 终端可见列宽: ANSI 序列计 0, 全角计 2, 其余计 1 (测试内独立实现) */
    private static int visibleWidth(String text) {
        int width = 0;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '\u001B' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < text.length() && !Character.isLetter(text.charAt(j))) {
                    j++;
                }
                i = Math.min(j + 1, text.length());
                continue;
            }
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            width += isWide(codePoint) ? 2 : 1;
        }
        return width;
    }

    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE4F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD);
    }
}
