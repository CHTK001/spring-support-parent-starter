package com.chua.starter.eduai.support.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 整卷二维码解码工具。
 *
 * <p>生成的试卷在每页页脚打印 「PAPER:试卷ID」二维码；阅卷上传图片后解码该二维码，
 * 即可把外来卷（ANY）自动归回内置卷（SELF），并按试卷 ID + 题号回数据库判分。</p>
 *
 * @author CH
 * @version 4.0.0.42
 */
public final class PaperQrDecoder {

    private static final Pattern PAPER_ID_PATTERN = Pattern.compile(
            "(?i)^PAPER\\s*[:：]\\s*(\\d{1,19})$");

    private PaperQrDecoder() {
    }

    /**
     * 从图片字节流中解码整卷二维码。
     *
     * @param imageData 上传的试卷图片（JPEG/PNG 等 ImageIO 可读格式）
     * @return 二维码里的试卷 ID；未识别出整卷二维码时返回 null
     */
    public static Long decodePaperId(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                return null;
            }
            try {
                return parsePaperId(decode(image, false));
            } catch (NotFoundException ignored) {
                return parsePaperId(decode(image, true));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String decode(BufferedImage image, boolean inverted) throws NotFoundException {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        if (inverted) {
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i];
                int a = (argb >>> 24) & 0xFF;
                int r = 255 - ((argb >>> 16) & 0xFF);
                int g = 255 - ((argb >>> 8) & 0xFF);
                int b = 255 - (argb & 0xFF);
                pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }

    private static Long parsePaperId(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PAPER_ID_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}