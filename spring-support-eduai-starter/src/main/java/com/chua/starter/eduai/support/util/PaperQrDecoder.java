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
 * 即可把外来卷（ANY）自动归回内置卷（SELF），并按试卷 ID + 题号回数据库判分。
 * 当卷面已经绑定学生/考试时，二维码可追加 {@code ;STUDENT:学号} 与 {@code ;EXAM:考试ID}，
 * 阅卷端据此自动绑定学生并参与重复提交、替考身份校验。</p>
 *
 * @author CH
 * @version 4.0.0.42
 */
public final class PaperQrDecoder {

    private static final Pattern QR_LINE_PATTERN = Pattern.compile(
            "^(PAPER|STUDENT|EXAM)\\s*[:：]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private PaperQrDecoder() {
    }

    /**
     * 从图片字节流中解码整卷二维码。
     *
     * @param imageData 上传的试卷图片（JPEG/PNG 等 ImageIO 可读格式）
     * @return 二维码里的试卷 ID；未识别出整卷二维码时返回 null
     */
    public static Long decodePaperId(byte[] imageData) {
        QrInfo info = decodeQrInfo(imageData);
        return info == null ? null : info.paperId();
    }

    /**
     * 解析整卷二维码携带的试卷、学生、考试信息。
     *
     * @param imageData 上传的试卷图片
     * @return 二维码信息；未识别出可用的整卷二维码时返回 null
     */
    public static QrInfo decodeQrInfo(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                return null;
            }
            try {
                return parseQrInfo(decode(image, false));
            } catch (NotFoundException ignored) {
                return parseQrInfo(decode(image, true));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解码
     * @param image 参数
     * @param inverted 布尔值
     * @return 返回值
     * @throws NotFoundException 异常
    */
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

    /**
     * parse Qr Info
     * @param text 字符串
     * @return 返回值
    */
    private static QrInfo parseQrInfo(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Long paperId = null;
        Long examId = null;
        String studentNo = null;
        String[] segments = text.trim().split("[;；]");
        for (String segment : segments) {
            Matcher matcher = QR_LINE_PATTERN.matcher(segment.trim());
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).toUpperCase();
            String value = matcher.group(2).trim();
            if (value.isEmpty()) {
                continue;
            }
            if ("PAPER".equals(key)) {
                paperId = parseLong(value);
            } else if ("EXAM".equals(key)) {
                examId = parseLong(value);
            } else if ("STUDENT".equals(key)) {
                studentNo = value;
            }
        }
        if (paperId == null && examId == null && studentNo == null) {
            return null;
        }
        return new QrInfo(paperId, examId, studentNo);
    }

    /**
     * parse Long
     * @param value 字符串
     * @return 返回值
    */
    private static Long parseLong(String value) {
        if (value == null || !value.matches("\\d{1,19}")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 整卷二维码信息。
     *
     * @param paperId   试卷 ID；二维码可只携带试卷归属
     * @param examId    考试 ID，未携带时为 null
     * @param studentNo 学生学号，未携带时为 null
     */
    public record QrInfo(Long paperId, Long examId, String studentNo) {
    }
}