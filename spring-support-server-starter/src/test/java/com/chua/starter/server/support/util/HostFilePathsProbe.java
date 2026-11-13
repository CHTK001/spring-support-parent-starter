package com.chua.starter.server.support.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主机文件路径工具的回归探针。
 *
 * <p>这些用例守的是安全边界与口径一致：key 从请求里来，归一化一旦放行
 * {@code ..}，删除与重命名就能打到存储根之外；类型判定一旦和前端图标对不上，
 * 界面上就会出现「jar 显示成压缩包」。两者都不会抛异常，只会悄悄做错事。</p>
 *
 * <p>按本仓约定，非 example 模块的回归脚本以 {@code *Probe} 命名（surefire 已配
 * 包含该模式），不新增 {@code *Test} 类。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
class HostFilePathsProbe {

    /**
     * 目录归一：统一分隔符、去掉首尾斜杠、补一个结尾斜杠。
     */
    @Test
    void normalizeDirKeepsSingleTrailingSlash() {
        assertEquals("", HostFilePaths.normalizeDir(null));
        assertEquals("", HostFilePaths.normalizeDir(""));
        assertEquals("", HostFilePaths.normalizeDir("/"));
        assertEquals("uploads/", HostFilePaths.normalizeDir("uploads"));
        assertEquals("uploads/release/", HostFilePaths.normalizeDir("/uploads//release/"));
        assertEquals("uploads/release/", HostFilePaths.normalizeDir("uploads\\release"));
    }

    /**
     * 穿越段必须被拒：这是删除 / 重命名的第一道闸。
     */
    @Test
    void normalizeKeyRejectsTraversal() {
        assertNull(HostFilePaths.normalizeKey("../etc/passwd"));
        assertNull(HostFilePaths.normalizeKey("uploads/../../etc"));
        assertEquals("uploads/app.jar", HostFilePaths.normalizeKey("/uploads/./app.jar/"));
    }

    /**
     * 名称校验：分隔符、盘符、点号目录都算非法。
     */
    @Test
    void isSafeNameRejectsPathLikeNames() {
        assertTrue(HostFilePaths.isSafeName("app-4.0.0.jar"));
        assertTrue(HostFilePaths.isSafeName("发布产物"));
        assertFalse(HostFilePaths.isSafeName(""));
        assertFalse(HostFilePaths.isSafeName(" "));
        assertFalse(HostFilePaths.isSafeName("."));
        assertFalse(HostFilePaths.isSafeName(".."));
        assertFalse(HostFilePaths.isSafeName("a/b"));
        assertFalse(HostFilePaths.isSafeName("a\\b"));
        assertFalse(HostFilePaths.isSafeName("C:app"));
    }

    /**
     * 子项 key 拼接：父目录为空时不能出现前导斜杠。
     */
    @Test
    void childJoinsWithoutLeadingSlash() {
        assertEquals("app.jar", HostFilePaths.child("", "app.jar"));
        assertEquals("uploads/app.jar", HostFilePaths.child("uploads/", "app.jar"));
        assertNull(HostFilePaths.child("uploads", "../app.jar"));
    }

    /**
     * 父目录与名称：顶层条目的父目录是空串而不是 null。
     */
    @Test
    void parentAndNameOfTopLevelEntry() {
        assertEquals("", HostFilePaths.parentOf("app.jar"));
        assertEquals("app.jar", HostFilePaths.nameOf("app.jar"));
        assertEquals("a/b", HostFilePaths.parentOf("a/b/c.jar"));
        assertEquals("c.jar", HostFilePaths.nameOf("a/b/c.jar"));
    }

    /**
     * 类型判定：发布场景最常打交道的几个后缀必须落在预期分类。
     */
    @Test
    void kindOfMapsReleaseArtifacts() {
        assertEquals(HostFilePaths.KIND_FOLDER, HostFilePaths.kindOf("uploads", true));
        assertEquals(HostFilePaths.KIND_PACKAGE, HostFilePaths.kindOf("app.jar", false));
        assertEquals(HostFilePaths.KIND_PACKAGE, HostFilePaths.kindOf("app.war", false));
        assertEquals(HostFilePaths.KIND_ARCHIVE, HostFilePaths.kindOf("app.tar.gz", false));
        assertEquals(HostFilePaths.KIND_TEXT, HostFilePaths.kindOf("application.yml", false));
        assertEquals(HostFilePaths.KIND_CODE, HostFilePaths.kindOf("start.sh", false));
        assertEquals(HostFilePaths.KIND_IMAGE, HostFilePaths.kindOf("logo.PNG", false));
        assertEquals(HostFilePaths.KIND_OTHER, HostFilePaths.kindOf("app.bin", false));
        assertEquals(HostFilePaths.KIND_OTHER, HostFilePaths.kindOf("README", false));
    }

    /**
     * 文本预览只对文本 / 代码开放；二进制不允许进预览接口。
     */
    @Test
    void onlyTextLikeFilesArePreviewable() {
        assertTrue(HostFilePaths.isPreviewableText("application.yml"));
        assertTrue(HostFilePaths.isPreviewableText("start.sh"));
        assertFalse(HostFilePaths.isPreviewableText("app.jar"));
        assertFalse(HostFilePaths.isPreviewableText("logo.png"));
    }

    /**
     * 内容类型：图片 / 文本 / PDF 要能被浏览器内联，其余一律二进制。
     */
    @Test
    void contentTypeFollowsExtension() {
        assertEquals("image/png", HostFilePaths.contentTypeOf("logo.png"));
        assertEquals("image/svg+xml", HostFilePaths.contentTypeOf("logo.svg"));
        assertEquals("application/pdf", HostFilePaths.contentTypeOf("manual.pdf"));
        assertTrue(HostFilePaths.contentTypeOf("application.yml").startsWith("text/plain"));
        assertEquals("application/octet-stream", HostFilePaths.contentTypeOf("app.jar"));
    }
}
