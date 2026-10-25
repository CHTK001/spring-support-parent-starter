package com.chua.starter.eduai.support.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 整卷二维码解码测试。
 *
 * <p>卷面打印的二维码内容为 {@code PAPER:{paperId}}；阅卷上传图片后解码该二维码，
 * 即可把外来卷（ANY）按试卷 ID 归回内置卷（SELF）。本测试固化解码器行为，
 * 防止前缀解析或图像解码被后续改动破坏。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class PaperQrDecoderTest {

    /**
     * 用 ZXing 生成一张内容为 {@code PAPER:9043} 的 PNG 二维码，
     * 解码后应返回 9043。
     *
     * <p>当前端导出 A4 图片时，页脚整卷二维码正是用同一前缀生成，
     * 后端只有能解出这一格式才能把外来卷归回内置卷。</p>
     */
    @Test
    @DisplayName("PAPER:9043 二维码可解码为试卷 ID 9043")
    void decodePaperId_shouldReadPaperQr() throws Exception {
        byte[] png = createQrPng("PAPER:9043");
        assertEquals(9043L, PaperQrDecoder.decodePaperId(png),
                "整卷二维码应解码出试卷 ID 9043");
    }

    /**
     * 非整卷二维码（如题项二维码）不应被误判为整卷归属。
     */
    @Test
    @DisplayName("非 PAPER 前缀二维码返回 null，不误归类内置卷")
    void decodePaperId_shouldIgnoreNonPaperQr() throws Exception {
        byte[] png = createQrPng("P2026101104232123F2-036");
        assertNull(PaperQrDecoder.decodePaperId(png),
                "题项二维码不应被当成整卷二维码");
    }

    /**
     * 整卷二维码可携带学生与考试信息：PAPER + STUDENT + EXAM 三段应全部解出。
     */
    @Test
    @DisplayName("PAPER;STUDENT;EXAM 二维码解析出试卷/学生/考试")
    void decodeQrInfo_shouldReadIdentitySegments() throws Exception {
        byte[] png = createQrPng("PAPER:9001;STUDENT:20260001;EXAM:88");
        PaperQrDecoder.QrInfo info = PaperQrDecoder.decodeQrInfo(png);
        assertEquals(9001L, info.paperId(), "试卷 ID 应解析为 9001");
        assertEquals("20260001", info.studentNo(), "学生学号应解析为 20260001");
        assertEquals(88L, info.examId(), "考试 ID 应解析为 88");
    }

    /**
     * 只带试卷归属的二维码，学生/考试保持空，兼容纯归卷场景。
     */
    @Test
    @DisplayName("仅 PAPER 二维码解析出试卷、学生与考试为空")
    void decodeQrInfo_shouldKeepIdentityEmpty() throws Exception {
        byte[] png = createQrPng("PAPER:9043");
        PaperQrDecoder.QrInfo info = PaperQrDecoder.decodeQrInfo(png);
        assertEquals(9043L, info.paperId());
        assertNull(info.studentNo(), "未携带学生信息时学号应为空");
        assertNull(info.examId(), "未携带考试信息时考试 ID 应为空");
    }

    /**
     * 大小写、两端空白、分段 order 变化都应能解析（打印机/字体差异的容错）。
     */
    @Test
    @DisplayName("大小写与空白容错：paper 小写 + 多余空格 + 段序调换")
    void decodeQrInfo_shouldBeCaseAndSpaceInsensitive() throws Exception {
        byte[] png = createQrPng("  paper:9001 ; EXAM: 77 ; student:20260002  ");
        PaperQrDecoder.QrInfo info = PaperQrDecoder.decodeQrInfo(png);
        assertEquals(9001L, info.paperId(), "大小写/空白不应影响试卷 ID");
        assertEquals("20260002", info.studentNo(), "大小写/空白不应影响学生学号");
        assertEquals(77L, info.examId(), "大小写/空白不应影响考试 ID");
    }

    /**
     * 中文冒号也是合法分隔符（生成端与平台打印都可产出全角冒号）。
     */
    @Test
    @DisplayName("全角冒号分隔可解析")
    void decodeQrInfo_shouldAcceptFullWidthColon() throws Exception {
        byte[] png = createQrPng("PAPER：9043；STUDENT：20260003；EXAM：66");
        PaperQrDecoder.QrInfo info = PaperQrDecoder.decodeQrInfo(png);
        assertEquals(9043L, info.paperId());
        assertEquals("20260003", info.studentNo());
        assertEquals(66L, info.examId());
    }

    /**
     * 重复段取最后一个（同键覆盖，键值以最后一次为准，避免拼接遗漏旧值）。
     */
    @Test
    @DisplayName("重复键段取最后一个值")
    void decodeQrInfo_shouldTakeLastDuplicateSegment() throws Exception {
        byte[] png = createQrPng("PAPER:9001;PAPER:9002;STUDENT:20260001");
        PaperQrDecoder.QrInfo info = PaperQrDecoder.decodeQrInfo(png);
        assertEquals(9002L, info.paperId(), "重复 PAPER 段应取最后一个值");
        assertEquals("20260001", info.studentNo());
    }

    /**
     * 携带了 STUDENT/EXAM 但没有 PAPER → 应解出身份信息且试卷归属为空
     * （阅卷端只按 PAPER 段归卷，其余段不触发误归集）。
     */
    @Test
    @DisplayName("仅 STUDENT/EXAM 无 PAPER → 身份可解、试卷为空")
    void decodeQrInfo_shouldReadIdentityWithoutPaper() throws Exception {
        byte[] png = createQrPng("STUDENT:20260001;EXAM:88");
        PaperQrDecoder.QrInfo info = PaperQrDecoder.decodeQrInfo(png);
        assertNull(info.paperId(), "无 PAPER 段时试卷归属应为空");
        assertEquals("20260001", info.studentNo());
        assertEquals(88L, info.examId());
    }

    /**
     * 只有垃圾内容 → 返回 null，不误造 QrInfo。
     */
    @Test
    @DisplayName("无意义内容返回 null")
    void decodeQrInfo_shouldIgnoreGarbage() throws Exception {
        byte[] png = createQrPng("HELLO WORLD;FOO:BAR");
        assertNull(PaperQrDecoder.decodeQrInfo(png), "垃圾内容不应被当成整卷二维码");
    }

    /**
     * 构造一个可被 ImageIO 读取的二维码 PNG 字节流。
     */
    private byte[] createQrPng(String content) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, String> hints = Map.of(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 180, 180, hints);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}