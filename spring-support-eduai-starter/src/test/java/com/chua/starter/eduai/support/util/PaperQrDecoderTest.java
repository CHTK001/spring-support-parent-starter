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