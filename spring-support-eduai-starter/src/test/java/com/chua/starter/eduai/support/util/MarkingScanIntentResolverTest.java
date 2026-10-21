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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阅卷扫码归类意图解析测试。
 *
 * <p>整卷二维码内容为 {@code PAPER:{paperId}}；上传第一张图若能解出该二维码，
 * 无论 ANY / SELF、是否手动传 paperId，都应自动归为内置卷（SELF）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class MarkingScanIntentResolverTest {

    /**
     * 外来卷（ANY、未传 paperId）上传带整卷二维码的图片 → 自动归回内置卷 9043。
     */
    @Test
    @DisplayName("ANY 无 paperId + 整卷二维码 → 归为内置卷 9043")
    void anyWithoutPaperId_shouldClassifyByQr() throws Exception {
        MarkingScanIntentResolver.Resolution r =
                MarkingScanIntentResolver.resolve("ANY", createQrPng("PAPER:9043"), null);
        assertFalse(r.anyMode(), "二维码命中后应归为内置卷 SELF");
        assertEquals(9043L, r.paperId(), "paperId 应取自二维码");
        assertTrue(r.fromQr(), "应标记为二维码识别而来");
    }

    /**
     * SELF 模式未传 paperId，但卷面带二维码 → 也能用二维码补出试卷归属。
     */
    @Test
    @DisplayName("SELF 无 paperId + 整卷二维码 → 用二维码补出 9043")
    void selfWithoutPaperId_shouldFillByQr() throws Exception {
        MarkingScanIntentResolver.Resolution r =
                MarkingScanIntentResolver.resolve("SELF", createQrPng("PAPER:9043"), null);
        assertFalse(r.anyMode());
        assertEquals(9043L, r.paperId(), "SELF 且无 paperId 时由二维码补出试卷 ID");
        assertTrue(r.fromQr());
    }

    /**
     * ANY 模式且图片无整卷二维码 → 保持 ANY、paperId 不变（外来卷）。
     */
    @Test
    @DisplayName("ANY 无二维码 → 保持外来卷 ANY")
    void anyWithoutQr_shouldStayAny() throws Exception {
        MarkingScanIntentResolver.Resolution r =
                MarkingScanIntentResolver.resolve("ANY", new byte[]{1, 2, 3}, null);
        assertTrue(r.anyMode(), "无二维码时保持任意卷");
        assertNull(r.paperId());
        assertFalse(r.fromQr());
    }

    /**
     * SELF 模式且图片无整卷二维码 → 保持 SELF，沿用前端 paperId。
     */
    @Test
    @DisplayName("SELF 无二维码 → 沿用前端 paperId")
    void selfWithoutQr_shouldKeepPaperId() throws Exception {
        MarkingScanIntentResolver.Resolution r =
                MarkingScanIntentResolver.resolve("SELF", new byte[]{1, 2, 3}, 42L);
        assertFalse(r.anyMode());
        assertEquals(42L, r.paperId());
        assertFalse(r.fromQr());
    }

    /**
     * 题项二维码（非 PAPER 前缀）不应被误判为整卷归属。
     */
    @Test
    @DisplayName("题项二维码 → 不误判整卷归属")
    void questionQr_shouldNotClassify() throws Exception {
        MarkingScanIntentResolver.Resolution r =
                MarkingScanIntentResolver.resolve("ANY", createQrPng("P2026101104232123F2-036"), null);
        assertTrue(r.anyMode(), "题项二维码不是整卷二维码，应保持 ANY");
        assertNull(r.paperId());
        assertFalse(r.fromQr());
    }

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