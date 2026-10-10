package com.chua.starter.filesystem.support.spi;

import com.chua.starter.filesystem.support.spi.filter.BlurImageFilterProvider;
import com.chua.starter.filesystem.support.spi.filter.GrayscaleImageFilterProvider;
import com.chua.starter.filesystem.support.spi.filter.InvertImageFilterProvider;
import com.chua.starter.filesystem.support.spi.filter.NoneImageFilterProvider;
import com.chua.starter.filesystem.support.spi.filter.SaturateImageFilterProvider;
import com.chua.starter.filesystem.support.spi.filter.SepiaImageFilterProvider;
import com.chua.starter.filesystem.support.spi.url.NoneUrlPreviewProvider;
import com.chua.starter.filesystem.support.spi.url.ServerUrlPreviewProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件存储 SPI 契约 —— 「能力声明」与「是否真正执行」必须自洽。
 *
 * <p>这两件事此前是分开配的，出错时彼此看不出来：</p>
 * <ul>
 *   * <li><b>URL 预览</b>：{@code openThirdPreview} 是一个布尔开关，「谁能处理外部地址」
 *       * 和「处理发生在哪一侧」被压成同一个值，扩展方接入自己的处理器时前端无从表达；</li>
 *   * <li><b>图片滤镜</b>：{@code imageFilter} 选中后只改前端 CSS，服务端图片管线从未
 *       * 按它处理过——选中「灰度」拿到的仍是原图字节。</li>
 * </ul>
 *
 * <p>现在两个能力都由 SPI 显式声明，本类钉住声明之间的关系：</p>
 * <ul>
 *   * <li>{@code isServerSide()} 为 true 的实现必须给出非空 {@code getPathParam()}，
 *       * 否则前端会把空参数拼进地址、后台也生不成处理步骤，选中它等于什么都没做；</li>
 *   * <li>{@code isServerSide()} 为 false 的实现必须**不给**路径参数，否则会往地址上
 *       * 塞一个后台无法执行的指令；</li>
 *   * <li>{@code supportsUrlPreview()} 只有后台拉取型实现返回 true——前端直连型实现
 *       * 拿不到票据与鉴权，走不通预览分发链路。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
class FileStorageSpiContractTest {

    /**
     * 内置图片滤镜实现（顺序与 {@code META-INF/extensions} 登记一致）。
     */
    private static final FileStorageImageFilterProvider[] FILTERS = {
            new NoneImageFilterProvider(),
            new GrayscaleImageFilterProvider(),
            new SepiaImageFilterProvider(),
            new InvertImageFilterProvider(),
            new BlurImageFilterProvider(),
            new SaturateImageFilterProvider(),
    };

    /**
     * 内置三方 URL 预览能力实现。
     */
    private static final FileStorageUrlPreviewProvider[] URL_PREVIEWERS = {
            new NoneUrlPreviewProvider(),
            new ServerUrlPreviewProvider(),
    };

    @Test
    @DisplayName("isServerSide 为 true 的实现必须给出图片路径参数（否则选中它等于没选）")
    void serverSideFiltersMustProvidePathParam() {
        for (FileStorageImageFilterProvider filter : FILTERS) {
            if (!filter.isServerSide()) {
                continue;
            }
            String path = filter.getPathParam();
            assertTrue(path != null && !path.isBlank(),
                    filter.getName() + " 声明由后台处理，却未给出图片路径参数");
            assertTrue(path.contains("="),
                    filter.getName() + " 的路径参数必须是 key=value 形式，实际: " + path);
        }
    }

    @Test
    @DisplayName("isServerSide 为 false 的实现不得给出图片路径参数（后台执行不了）")
    void clientSideFiltersMustNotProvidePathParam() {
        for (FileStorageImageFilterProvider filter : FILTERS) {
            if (filter.isServerSide()) {
                continue;
            }
            String path = filter.getPathParam();
            assertTrue(path == null || path.isBlank(),
                    filter.getName() + " 不由后台处理，却给出了路径参数 " + path
                            + "，会把无法执行的指令拼进图片地址");
        }
    }

    @Test
    @DisplayName("原图滤镜既不处理也不给参数，且 CSS 为空")
    void noneFilterIsInert() {
        FileStorageImageFilterProvider none = new NoneImageFilterProvider();
        assertEquals("NONE", none.getName(), "原图滤镜名必须为 NONE");
        assertFalse(none.isServerSide(), "原图无需后台处理");
        assertTrue(none.getPathParam() == null || none.getPathParam().isBlank(),
                "原图不得带处理参数");
        assertTrue(none.getCssFilter() == null || none.getCssFilter().isBlank(),
                "原图不得有 CSS 滤镜，否则同一处会被压一层");
    }

    @Test
    @DisplayName("后台可处理的滤镜（灰度/模糊）给出对应 URL 参数")
    void serverSideFiltersMapToUrlOps() {
        assertEquals("grayscale=1", new GrayscaleImageFilterProvider().getPathParam(),
                "灰度应对应 URL 参数 grayscale=1");
        assertEquals("blur=3", new BlurImageFilterProvider().getPathParam(),
                "模糊应对应 URL 参数 blur=3");
    }

    @Test
    @DisplayName("后台不支持的效果（反色/怀旧/鲜艳）只提供 CSS 预览")
    void cssOnlyFiltersHaveNoServerPath() {
        for (FileStorageImageFilterProvider filter : new FileStorageImageFilterProvider[]{
                new InvertImageFilterProvider(), new SepiaImageFilterProvider(), new SaturateImageFilterProvider()}) {
            assertFalse(filter.isServerSide(), filter.getName() + " 后台图片管线不提供该效果");
            assertTrue(filter.getPathParam() == null || filter.getPathParam().isBlank(),
                    filter.getName() + " 不应给出后台无法执行的路径参数");
            assertTrue(filter.getCssFilter() != null && !filter.getCssFilter().isBlank(),
                    filter.getName() + " 既然后台不处理，就必须有 CSS 预览兜底");
        }
    }

    @Test
    @DisplayName("只有后台拉取型实现支持 ?url=（前端直连走不通预览分发）")
    void onlyServerUrlPreviewerSupportsUrl() {
        FileStorageUrlPreviewProvider none = new NoneUrlPreviewProvider();
        FileStorageUrlPreviewProvider server = new ServerUrlPreviewProvider();

        assertEquals("NONE", none.getName(), "关闭态实现名必须为 NONE");
        assertFalse(none.supportsUrlPreview(), "关闭态不得放行 ?url=");

        assertEquals("SERVER", server.getName(), "后台实现名必须为 SERVER");
        assertTrue(server.supportsUrlPreview(), "后台实现必须放行 ?url=");
        assertFalse(server.getLabel() == null || server.getLabel().isBlank(),
                "后台实现必须有展示名，前端下拉要显示它");
    }

    @Test
    @DisplayName("URL 预览能力实现名全局唯一（写入配置后要能反查实现）")
    void urlPreviewerNamesAreUnique() {
        long distinct = java.util.Arrays.stream(URL_PREVIEWERS)
                .map(FileStorageUrlPreviewProvider::getName)
                .distinct()
                .count();
        assertEquals(URL_PREVIEWERS.length, distinct,
                "能力实现名重复会让配置值无法唯一对应一个实现");
    }
}
