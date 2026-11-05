// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.ai.paper;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 试卷图片预处理：按图片质量自适应选择处理策略。
 *
 * <p>两类输入的诉求相反，实测（同一批卷、只改检测尺度）：</p>
 * <ul>
 *   <li><b>干净扫描件</b>：长边归一化到 {@value #CLEAN_LONG_SIDE}（检测模型的训练尺度）时最准；
 *       放大到 1400/1600 反而变差（卷 21 题：21 行 → 11 行 / 10 行）。</li>
 *   <li><b>拍照退化件</b>（光照不均 + 噪点 + 小字）：先做照度校正（去阴影），再把长边提到
 *       {@value #DEGRADED_LONG_SIDE} 才准（卷 21 题：18 行 / 14 满分 → 21 行 / 20 满分）。</li>
 * </ul>
 *
 * <p>故按「照度背景极差」判据分流：均匀 → 仅归一到训练尺度；不均 → 照度校正 + 提尺度。
 * 只用 JDK 的 {@link BufferedImage}；任何失败都回退原图，绝不让预处理成为新的故障点。</p>
 *
 * <p>属于阅卷业务的图片预处理规则（判据与目标尺度都是按试卷实测调出来的），
 * 故归 eduai 业务模块，而不是 AI 基础包。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Slf4j
public final class PaperImagePreprocessor {

    private PaperImagePreprocessor() {
    }

    /**
     * 照度背景极差阈值：低于此值视为「照度均匀」（干净件）。
     */
    private static final int ILLUMINATION_SPREAD_THRESHOLD = 60;

    /**
     * 小于该长边一律直通（不缩放、不归一化）—— 此时检测器本来就不会缩放，改动只会带来风险。
     */
    private static final int MIN_PROCESS_SIDE = 1000;

    /**
     * 干净件的长边目标（检测模型训练尺度）。
     */
    private static final int CLEAN_LONG_SIDE = 960;

    /**
     * 退化件的长边目标（提高分辨率以救回小字）。
     */
    private static final int DEGRADED_LONG_SIDE = 1400;

    /**
     * 预处理：按图片质量自适应选择策略。
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
                log.warn("[PaperImage] 图片无法解码，跳过预处理（{} 字节）", imageData.length);
                return imageData;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            if (w < 8 || h < 8) {
                return imageData;
            }
            int[] gray = toGray(src, w, h);
            long[] integral = buildIntegral(gray, w, h);
            int radius = Math.max(8, Math.min(w, h) / 16);
            int[] spread = new int[]{255, 0};
            BufferedImage normalized = flatField(gray, integral, w, h, radius, spread);
            int diff = spread[1] - spread[0];
            int longSide = Math.max(w, h);

            // 小图直通：长边不足 MIN_PROCESS_SIDE 时，检测器本来就不会缩放（原路径行为），
            // 任何改动（灰度/归一化/放大）都会改变检测输入。实测把 900x390 的验收图
            // 归一化并放大到 1400 会让判分全变 0。
            if (longSide < MIN_PROCESS_SIDE) {
                log.info("[PaperImage] 长边 {} < {} → 原图直通", longSide, MIN_PROCESS_SIDE);
                return imageData;
            }
            if (diff < ILLUMINATION_SPREAD_THRESHOLD) {
                // 干净件：仅当长边超过检测模型的训练尺度时才缩（只缩不放），色彩与归一化都不动。
                if (longSide <= CLEAN_LONG_SIDE) {
                    log.info("[PaperImage] 照度均匀（极差={}）且长边 {} <= {} → 原图直通", diff, longSide, CLEAN_LONG_SIDE);
                    return imageData;
                }
                log.info("[PaperImage] 照度均匀（极差={}）→ 缩到长边 {}", diff, CLEAN_LONG_SIDE);
                BufferedImage scaled = scaleToLongSide(src, w, h, CLEAN_LONG_SIDE);
                return encode(scaled, imageData);
            }
            // 退化件：照度校正 + 提到更高分辨率
            log.info("[PaperImage] 照度不均（极差={}）→ 照度校正 + 长边 {}", diff, DEGRADED_LONG_SIDE);
            BufferedImage scaled = scaleToLongSide(normalized, w, h, DEGRADED_LONG_SIDE);
            return encode(scaled, imageData);
        } catch (Exception e) {
            log.warn("[PaperImage] 预处理失败，回退原图: {}", e.getMessage());
            return imageData;
        }
    }

    /**
     * 转灰度（BT.601 亮度）。
     *
     * @param src 原图
     * @param w   宽
     * @param h   高
     * @return 灰度数组
     */
    private static int[] toGray(BufferedImage src, int w, int h) {
        int[] gray = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y * w + x] = (r * 299 + g * 587 + b * 114) / 1000;
            }
        }
        return gray;
    }

    /**
     * 建积分图（O(1) 取任意矩形和）。
     *
     * @param gray 灰度
     * @param w    宽
     * @param h    高
     * @return 积分图
     */
    private static long[] buildIntegral(int[] gray, int w, int h) {
        long[] integral = new long[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            long rowSum = 0;
            for (int x = 0; x < w; x++) {
                rowSum += gray[y * w + x];
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + rowSum;
            }
        }
        return integral;
    }

    /**
     * flat-field 照度校正：{@code out = gray * 白场 / 大半径均值}。
     *
     * @param gray     灰度
     * @param integral 积分图
     * @param w        宽
     * @param h        高
     * @param radius   均值半径
     * @param spread   出参：返回照度背景的 [min, max]
     * @return 校正后的灰度图
     */
    private static BufferedImage flatField(int[] gray, long[] integral, int w, int h, int radius, int[] spread) {
        int white = 235;
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
                if (bg < spread[0]) {
                    spread[0] = bg;
                }
                if (bg > spread[1]) {
                    spread[1] = bg;
                }
                int v = (int) ((long) gray[y * w + x] * white / bg);
                out[y * w + x] = (byte) Math.max(0, Math.min(255, v));
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        img.getRaster().setDataElements(0, 0, w, h, out);
        return img;
    }

    /**
     * 等比缩放到指定长边。
     *
     * @param src      原图
     * @param w        原宽
     * @param h        原高
     * @param longSide 目标长边
     * @return 缩放后的图；已等于目标则原样返回
     */
    private static BufferedImage scaleToLongSide(BufferedImage src, int w, int h, int longSide) {
        int cur = Math.max(w, h);
        if (cur == longSide) {
            return src;
        }
        double ratio = (double) longSide / cur;
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage dst = new BufferedImage(nw, nh, src.getType() == 0 ? BufferedImage.TYPE_BYTE_GRAY : src.getType());
        var g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    /**
     * 编码为 PNG 字节。
     *
     * @param img      图
     * @param fallback 编码失败时的回退值
     * @return PNG 字节
     */
    private static byte[] encode(BufferedImage img, byte[] fallback) {
        try {
            var baos = new ByteArrayOutputStream(Math.max(1024, fallback.length / 2));
            if (!ImageIO.write(img, "png", baos)) {
                return fallback;
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("[PaperImage] 编码失败，回退原图: {}", e.getMessage());
            return fallback;
        }
    }
}