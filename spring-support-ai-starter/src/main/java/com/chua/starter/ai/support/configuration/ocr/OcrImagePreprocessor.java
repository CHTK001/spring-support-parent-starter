// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.ocr;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * OCR 前置图像增强（可选）。
 *
 * <p>对应的配置项全部在 {@code ocr} 分组里，且<b>都是非必填的增强项</b>：
 * 未配置 / 关闭 / 非法值时一律“原图直通”，不改变原有识别行为，也不会让识别失败。</p>
 *
 * <ul>
 *   <li>{@code ocr_max_image_size}：长边上限（像素）。超大图先等比缩小，降低耗时；
 *       留空或 ≤0 表示不缩放。</li>
 *   <li>{@code enhance_deskew}：自动纠偏。投影方差法估角（±8°），旋转校正；
 *       估计角度小于 0.3° 时不动图，避免无意义重采样。</li>
 *   <li>{@code enhance_contrast}：对比度拉伸（2%~98% 分位）。</li>
 *   <li>{@code enhance_denoise}：3×3 中值去噪。</li>
 *   <li>{@code enhance_binarize}：Otsu 二值化（默认关：对拍照/浅色卷面有害）。</li>
 * </ul>
 *
 * <p><b>坐标一致性</b>：缩放与旋转都会改变坐标系，识别结果必须映射回原图，
 * 否则标注框与上层（试卷版面联动）坐标全部错位。{@link Result#mapBack} 负责逆变换。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class OcrImagePreprocessor {

    /**
     * 纠偏估计的最大角度（度）
     */
    private static final double MAX_DESKEW_DEG = 8.0;

    /**
     * 小于该角度视为无需纠偏，避免无意义重采样
     */
    private static final double MIN_DESKEW_DEG = 0.3;

    /**
     * 估角时把图缩到这个宽度，控制耗时
     */
    private static final int ANGLE_PROBE_WIDTH = 480;

    /**
     * 预处理参数（全部可选）
     */
    public record Options(int maxSize, boolean deskew, boolean contrast, boolean denoise, boolean binarize) {

        /**
         * 是否完全不需要预处理。
         *
         * @return true 表示原图直通
         */
        public boolean noop() {
            return (maxSize <= 0) && !deskew && !contrast && !denoise && !binarize;
        }
    }

    /**
     * 预处理结果：处理后的字节 + 逆变换所需参数。
     */
    public static final class Result {

        /**
         * 处理后的图片字节（原图直通时就是入参本身）
         */
        private final byte[] data;

        /**
         * 缩放比例（处理后 / 原图）
         */
        private final double scale;

        /**
         * 旋转角（弧度，顺时针为正）
         */
        private final double angleRad;

        /**
         * 处理后图片中心
         */
        private final double cx;

        private final double cy;

        /**
         * Result
         * @param angleRad 双精度数
         * @param cx 双精度数
         * @param cy 双精度数
         * @param data 参数
         * @param scale 双精度数
         * @return 返回值
        */
        Result(byte[] data, double scale, double angleRad, double cx, double cy) {
            this.data = data;
            this.scale = scale;
            this.angleRad = angleRad;
            this.cx = cx;
            this.cy = cy;
        }

        /**
         * 处理后的图片字节。
         *
         * @return 图片字节
         */
        public byte[] data() {
            return data;
        }

        /**
         * 是否发生了几何变换（缩放或旋转）。
         *
         * @return true 表示坐标需要逆映射
         */
        public boolean transformed() {
            return scale != 1.0d || angleRad != 0.0d;
        }

        /**
         * 把「处理后坐标」映射回「原图坐标」。
         *
         * <p>顺序与正向相反：先绕处理后图片中心反旋转，再除以缩放比。</p>
         *
         * @param x 处理后 x
         * @param y 处理后 y
         * @return 原图坐标 {x, y}
         */
        public double[] mapBack(double x, double y) {
            if (!transformed()) {
                return new double[]{x, y};
            }
            double dx = x - cx;
            double dy = y - cy;
            double cos = Math.cos(-angleRad);
            double sin = Math.sin(-angleRad);
            double rx = dx * cos - dy * sin;
            double ry = dx * sin + dy * cos;
            double sx = (cx + rx) / scale;
            double sy = (cy + ry) / scale;
            return new double[]{sx, sy};
        }

        /**
         * 把「原图坐标」映射到「处理后坐标」（区域识别时用于裁剪）。
         *
         * @param x 原图 x
         * @param y 原图 y
         * @return 处理后坐标 {x, y}
         */
        public double[] mapForward(double x, double y) {
            if (!transformed()) {
                return new double[]{x, y};
            }
            double sx = x * scale;
            double sy = y * scale;
            double dx = sx - cx;
            double dy = sy - cy;
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            return new double[]{cx + dx * cos - dy * sin, cy + dx * sin + dy * cos};
        }
    }

    private OcrImagePreprocessor() {
    }

    /**
     * 按参数做预处理；参数全关或解码失败时原图直通。
     *
     * @param imageData 原图字节
     * @param options   参数
     * @return 预处理结果
     */
    public static Result process(byte[] imageData, Options options) {
        if (imageData == null || imageData.length == 0 || options == null || options.noop()) {
            return passthrough(imageData);
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageData));
            if (src == null) {
                return passthrough(imageData);
            }
            BufferedImage work = src;
            double scale = 1.0d;
            int maxSize = options.maxSize();
            if (maxSize > 0) {
                int longest = Math.max(work.getWidth(), work.getHeight());
                if (longest > maxSize) {
                    scale = (double) maxSize / longest;
                    work = resize(work, (int) Math.round(work.getWidth() * scale),
                            (int) Math.round(work.getHeight() * scale));
                }
            }
            double angle = 0.0d;
            if (options.deskew()) {
                angle = estimateSkew(work);
                if (Math.abs(angle) >= Math.toRadians(MIN_DESKEW_DEG)) {
                    work = rotate(work, angle);
                } else {
                    angle = 0.0d;
                }
            }
            if (options.contrast()) {
                work = stretchContrast(work);
            }
            if (options.denoise()) {
                work = medianDenoise(work);
            }
            if (options.binarize()) {
                work = otsuBinarize(work);
            }
            byte[] out = encodePng(work);
            if (out == null) {
                return passthrough(imageData);
            }
            log.debug("[OcrPreprocess] scale={} angle={}° {}x{} -> {}x{}",
                    String.format("%.3f", scale), String.format("%.2f", Math.toDegrees(angle)),
                    src.getWidth(), src.getHeight(), work.getWidth(), work.getHeight());
            return new Result(out, scale, angle, work.getWidth() / 2.0d, work.getHeight() / 2.0d);
        } catch (Exception ex) {
            // 增强是可选能力：任何异常都退回原图，绝不让识别失败
            log.warn("[OcrPreprocess] 预处理失败，按原图处理: {}", ex.getMessage());
            return passthrough(imageData);
        }
    }

    /**
     * passthrough
     * @param imageData 参数
     * @return 返回值
    */
    private static Result passthrough(byte[] imageData) {
        return new Result(imageData, 1.0d, 0.0d, 0.0d, 0.0d);
    }

    /**
     * 原图直通结果（未开启任何增强时使用，避免重复解码）。
     *
     * @param imageData 原图字节
     * @return 直通结果
     */
    public static Result passthroughOf(byte[] imageData) {
        return passthrough(imageData);
    }

    /**
     * resize
     * @param h 整数
     * @param src 参数
     * @param w 整数
     * @return 返回值
    */
    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * 投影方差法估角：把图按候选角度旋转后统计行投影方差，方差最大者即「文字最水平」。
     *
     * @param src 待估计图片（已缩放）
     * @return 需要施加的旋转角（弧度）
     */
    private static double estimateSkew(BufferedImage src) {
        BufferedImage probe = src;
        if (src.getWidth() > ANGLE_PROBE_WIDTH) {
            double s = (double) ANGLE_PROBE_WIDTH / src.getWidth();
            probe = resize(src, ANGLE_PROBE_WIDTH, Math.max(1, (int) Math.round(src.getHeight() * s)));
        }
        int w = probe.getWidth();
        int h = probe.getHeight();
        int[] gray = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = probe.getRGB(x, y);
                int lum = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                gray[y * w + x] = lum;
            }
        }
        double bestAngle = 0.0d;
        double bestScore = -1.0d;
        for (double deg = -MAX_DESKEW_DEG; deg <= MAX_DESKEW_DEG; deg += 0.5d) {
            double score = projectionVariance(gray, w, h, Math.toRadians(deg));
            if (score > bestScore) {
                bestScore = score;
                bestAngle = deg;
            }
        }
        return Math.toRadians(bestAngle);
    }

    /**
     * 给定角度下的行投影方差（越大表示文字行越整齐）。
     * @param angleRad 双精度数
     * @param gray 参数
     * @param h 整数
     * @param w 整数
     * @return 返回值
    */
    private static double projectionVariance(int[] gray, int w, int h, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double cx = w / 2.0d;
        double cy = h / 2.0d;
        int bins = h;
        int[] hist = new int[bins];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int lum = gray[y * w + x];
                if (lum > 200) {
                    continue;
                }
                double dx = x - cx;
                double dy = y - cy;
                double ny = cy + dx * sin + dy * cos;
                int b = (int) ny;
                if (b >= 0 && b < bins) {
                    hist[b]++;
                }
            }
        }
        double mean = 0.0d;
        for (int v : hist) {
            mean += v;
        }
        mean /= bins;
        double var = 0.0d;
        for (int v : hist) {
            double d = v - mean;
            var += d * d;
        }
        return var;
    }

    /**
     * rotate
     * @param angleRad 双精度数
     * @param src 参数
     * @return 返回值
    */
    private static BufferedImage rotate(BufferedImage src, double angleRad) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform at = new AffineTransform();
            at.rotate(angleRad, w / 2.0d, h / 2.0d);
            g.drawImage(src, at, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * 对比度拉伸：按 2% / 98% 分位把灰度拉满，按同一比例作用到各通道。
     * @param src 参数
     * @return 返回值
    */
    private static BufferedImage stretchContrast(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int lum = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                hist[lum]++;
            }
        }
        long total = (long) w * h;
        long lowCut = total / 50L;
        long highCut = total - lowCut;
        int low = 0;
        int high = 255;
        long acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= lowCut) {
                low = i;
                break;
            }
        }
        acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= highCut) {
                high = i;
                break;
            }
        }
        if (high - low < 16) {
            return src;
        }
        double k = 255.0d / (high - low);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = clamp((int) (((rgb >> 16 & 0xFF) - low) * k));
                int gg = clamp((int) (((rgb >> 8 & 0xFF) - low) * k));
                int b = clamp((int) (((rgb & 0xFF) - low) * k));
                out.setRGB(x, y, (r << 16) | (gg << 8) | b);
            }
        }
        return out;
    }

    /**
     * median Denoise
     * @param src 参数
     * @return 返回值
    */
    private static BufferedImage medianDenoise(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] r = new int[9];
        int[] g = new int[9];
        int[] b = new int[9];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.min(w - 1, Math.max(0, x + dx));
                        int yy = Math.min(h - 1, Math.max(0, y + dy));
                        int rgb = src.getRGB(xx, yy);
                        r[n] = rgb >> 16 & 0xFF;
                        g[n] = rgb >> 8 & 0xFF;
                        b[n] = rgb & 0xFF;
                        n++;
                    }
                }
                java.util.Arrays.sort(r);
                java.util.Arrays.sort(g);
                java.util.Arrays.sort(b);
                out.setRGB(x, y, (r[4] << 16) | (g[4] << 8) | b[4]);
            }
        }
        return out;
    }

    /**
     * otsu Binarize
     * @param src 参数
     * @return 返回值
    */
    private static BufferedImage otsuBinarize(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int lum = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                hist[lum]++;
            }
        }
        long total = (long) w * h;
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += (double) i * hist[i];
        }
        double sumB = 0;
        long wB = 0;
        double maxVar = -1;
        int threshold = 128;
        for (int i = 0; i < 256; i++) {
            wB += hist[i];
            if (wB == 0) {
                continue;
            }
            long wF = total - wB;
            if (wF == 0) {
                break;
            }
            sumB += (double) i * hist[i];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double var = (double) wB * wF * (mB - mF) * (mB - mF);
            if (var > maxVar) {
                maxVar = var;
                threshold = i;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int lum = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                int v = lum > threshold ? 255 : 0;
                out.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    /**
     * clamp
     * @param v 整数
     * @return 返回值
    */
    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /**
     * encode Png
     * @param image 参数
     * @return 返回值
    */
    private static byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }
}