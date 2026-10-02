package com.chua.starter.eduai.ai;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 试卷图片预处理：照度校正（去阴影/光照不均）+ 灰度归一化。
 *
 * <p>拍照卷的常见退化是「整页光照不均」——一侧亮一侧暗（纸张弯曲/侧光/手机阴影）。
 * 实测拍照退化版 21 题只出 16 行，失败点集中在暗部：题号被识别成别的字符（{@code D→0}）、
 * 题号与作答被切成不同文字块。这类误差靠调模型解决不了，属于输入质量问题。</p>
 *
 * <p>做法是经典的 <b>flat-field 校正</b>：用大半径均值滤波估计照度背景 {@code bg}，
 * 再按 {@code out = gray * 白场 / max(bg, 1)} 归一化 —— 即「把纸的底色拉平成白」。
 * 只用 JDK 的 {@link BufferedImage}，不引第三方；任何失败都回退原图，绝不让预处理成为新的故障点。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class EduImagePreprocessor {

    private EduImagePreprocessor() {
    }

    /**
     * 照度背景极差阈值：低于此值视为「照度均匀」，不做归一化。
     */
    private static final int ILLUMINATION_SPREAD_THRESHOLD = 60;

    /**
     * 预处理：转为灰度并做照度校正。
     *
     * @param imageData 原图字节
     * @return 处理后的 PNG 字节；无法解码或处理失败时原样返回入参
     */
    public static byte[] preprocess(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return imageData;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageData));
            if (src == null) {
                log.warn("[EduImg] 图片无法解码，跳过预处理（{} 字节）", imageData.length);
                return imageData;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            if (w < 8 || h < 8) {
                return imageData;
            }
            // 1) 灰度
            int[] gray = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = src.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    // 亮度（BT.601）
                    gray[y * w + x] = (r * 299 + g * 587 + b * 114) / 1000;
                }
            }
            // 2) 大半径均值滤波估计照度背景（积分图，O(1) 每像素）
            int radius = Math.max(8, Math.min(w, h) / 16);
            long[] integral = new long[(w + 1) * (h + 1)];
            for (int y = 0; y < h; y++) {
                long rowSum = 0;
                for (int x = 0; x < w; x++) {
                    rowSum += gray[y * w + x];
                    integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + rowSum;
                }
            }
            int white = 235;
            int bgMin = 255;
            int bgMax = 0;
            byte[] out = new byte[w * h];
            for (int y = 0; y < h; y++) {
                int y0 = Math.max(0, y - radius);
                int y1 = Math.min(h - 1, y + radius);
                for (int x = 0; x < w; x++) {
                    int x0 = Math.max(0, x - radius);
                    int x1 = Math.min(w - 1, x + radius);
                    long sum = integral[(y1 + 1) * (w + 1) + (x1 + 1)]
                            - integral[y0 * (w + 1) + (x1 + 1)]
                            - integral[(y1 + 1) * (w + 1) + x0]
                            + integral[y0 * (w + 1) + x0];
                    long count = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
                    int bg = (int) Math.max(1, sum / Math.max(1, count));
                    if (bg < bgMin) {
                        bgMin = bg;
                    }
                    if (bg > bgMax) {
                        bgMax = bg;
                    }
                    int v = (int) ((long) gray[y * w + x] * white / bg);
                    out[y * w + x] = (byte) Math.max(0, Math.min(255, v));
                }
            }
            // 按需：照度均匀（干净扫描件）时不做归一化 —— 免得把本来就好的输入改坏。
            // 判据用「照度背景的极差」：干净件接近 0~20，带阴影/暗角的拍照件通常 > 60。
            if (bgMax - bgMin < ILLUMINATION_SPREAD_THRESHOLD) {
                log.info("[EduImg] 照度均匀（极差={}），跳过归一化，保持原图", bgMax - bgMin);
                return imageData;
            }
            log.info("[EduImg] 检测到照度不均（极差={}），执行归一化", bgMax - bgMin);
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            dst.getRaster().setDataElements(0, 0, w, h, out);
            var baos = new ByteArrayOutputStream(Math.max(1024, imageData.length / 2));
            if (!ImageIO.write(dst, "png", baos)) {
                return imageData;
            }
            byte[] result = baos.toByteArray();
            log.info("[EduImg] 预处理完成: {}x{} {}B -> {}B", w, h, imageData.length, result.length);
            return result;
        } catch (Exception e) {
            log.warn("[EduImg] 预处理失败，回退原图: {}", e.getMessage());
            return imageData;
        }
    }
}