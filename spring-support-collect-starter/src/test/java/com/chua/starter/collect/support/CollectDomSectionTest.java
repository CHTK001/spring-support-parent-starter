package com.chua.starter.collect.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分节正文抽取测试。
 *
 * <p>守住古文岛详情页的四种分节写法：{@code <p><strong>译文</strong>} 标准型、
 * 裸 {@code <strong>译文</strong>}、带编号的 {@code 其一译文 / 译文二}、
 * 以及同义标题（注解 / 部释 / 评析，另有「释文」实为译文）。</p>
 *
 * <p>重点是两处曾经出过脏数据的边界：</p>
 * <ol>
 *   <li>「注解」没被认成注释节，整块（译文 + 注释）被写进译文列；</li>
 *   <li>只有注释节的页面，{@code defaultIfEmpty} 回落把注释写进了译文列。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
class CollectDomSectionTest {

    @Test
    @DisplayName("标准型：p 内 strong 分节")
    void standardSections() {
        String html = "<div class=\"contyishang\"><p><strong>译文<br /></strong>甲<br />乙</p>"
                + "<p><strong>注释<br /></strong>丙</p></div>";

        assertEquals("甲\n乙", CollectDom.sectionText(html, "译文"));
        assertEquals("丙", CollectDom.sectionText(html, "注释"));
        assertTrue(CollectDom.sectionText(html, "赏析").isEmpty());
    }

    @Test
    @DisplayName("同义标题：注解 归一到注释")
    void aliasSections() {
        String html = "<div class=\"contyishang\"><p><strong>译文</strong><br />甲</p>"
                + "<p><strong>注解</strong><br />丙</p></div>";

        assertEquals("甲", CollectDom.sectionText(html, "译文"));
        assertEquals("丙", CollectDom.sectionText(html, "注释"));
    }

    @Test
    @DisplayName("释文是白话译文，不是注释")
    void shiwenIsTranslation() {
        String html = "<div class=\"contyishang\"><p>释文<br />甲</p>"
                + "<p>注释<br />丙</p></div>";

        assertEquals("甲", CollectDom.sectionText(html, "译文"));
        assertEquals("丙", CollectDom.sectionText(html, "注释"));
    }

    @Test
    @DisplayName("错字「部释」归一到注释")
    void bushiIsNotes() {
        String html = "<div class=\"contyishang\"><p>译文<br />甲</p>"
                + "<p>部释<br />丙</p></div>";

        assertEquals("甲", CollectDom.sectionText(html, "译文"));
        assertEquals("丙", CollectDom.sectionText(html, "注释"));
    }

    @Test
    @DisplayName("裸 strong：标题是块的直接子元素")
    void bareStrongSections() {
        String html = "<div class=\"contyishang\"><strong>译文</strong><br />甲<br />"
                + "<strong>注释</strong><br />丙</div>";

        assertEquals("甲", CollectDom.sectionText(html, "译文"));
        assertEquals("丙", CollectDom.sectionText(html, "注释"));
    }

    @Test
    @DisplayName("带编号：其一译文 / 其二译文 按顺序拼接")
    void numberedSections() {
        String html = "<div class=\"contyishang\">"
                + "<p><strong>其一译文</strong><br />甲</p>"
                + "<p><strong>其二译文</strong><br />乙</p>"
                + "<p><strong>其一注释</strong><br />丙</p>"
                + "<p><strong>其二注释</strong><br />丁</p></div>";

        assertEquals("甲\n乙", CollectDom.sectionText(html, "译文"));
        assertEquals("丙\n丁", CollectDom.sectionText(html, "注释"));
    }

    @Test
    @DisplayName("只有注释节：译文返回空串，不回落成注释")
    void notesOnlyBlock() {
        String html = "<div class=\"contyishang\"><p><strong>注释</strong><br />丙</p></div>";

        assertEquals("", CollectDom.sectionText(html, "译文"));
        assertEquals("", CollectDom.sectionTextOrBlock(html, "译文"));
        assertEquals("丙", CollectDom.sectionText(html, "注释"));
    }

    @Test
    @DisplayName("无分节标记：sectionText 返回 null，OrBlock 回落到整块正文")
    void blockWithoutSections() {
        String html = "<div class=\"contyishang\"><p>　　甲</p><p>　　乙</p></div>";

        assertNull(CollectDom.sectionText(html, "译文"));
        assertEquals(CollectDom.blockText(html), CollectDom.sectionTextOrBlock(html, "译文"));
        assertTrue(CollectDom.blockText(html).contains("甲"));
    }

    @Test
    @DisplayName("块标题与朗读锚点不进正文，复合标题不被当成分节标题")
    void blockTitleAndPlayLinkIgnored() {
        String html = "<div class=\"sons\"><div class=\"contyishang\">"
                + "<div style=\"height:30px\"><h2><span>译文及注释</span></h2>"
                + "<a href=\"javascript:PlayFanyi(1,2)\"><img src=\"/img/speaker.png\" /></a></div>"
                + "<p><strong>译文</strong><br />甲</p>"
                + "<p><strong>注释</strong><br />丙</p>"
                + "<div style=\"text-align:center\"><a href=\"javascript:fanyiClose(1)\">收起</a></div>"
                + "</div></div>";

        assertEquals("甲", CollectDom.sectionText(html, "译文"));
        assertEquals("丙", CollectDom.sectionText(html, "注释"));
        String block = CollectDom.blockText(html);
        assertFalse(block.contains("PlayFanyi"));
        assertFalse(block.contains("收起"));
    }

    @Test
    @DisplayName("译文段首 U+3000 缩进保留")
    void keepsIdeographicIndent() {
        String html = "<div class=\"contyishang\"><p><strong>译文</strong><br />\u3000\u3000甲</p></div>";

        assertEquals("\u3000\u3000甲", CollectDom.sectionText(html, "译文"));
    }
}
