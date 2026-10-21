package com.chua.starter.eduai.support.util;

/**
 * 阅卷扫码归类意图解析。
 *
 * <p>上传试卷时，第一张图片若带有整卷二维码（内容 {@code PAPER:{paperId}}），
 * 无论前端选择 ANY 还是 SELF、是否手动传了 paperId，都按二维码归回内置卷，
 * 后续链路以该试卷 ID + 识别题号从数据库取答案判分。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class MarkingScanIntentResolver {

    private MarkingScanIntentResolver() {
    }

    /**
     * 解析阅卷模式与试卷归属。
     *
     * @param mode      前端传入模式（SELF / ANY）
     * @param firstImage 第一张试卷图片字节流，可为 null
     * @param paperId   前端手动传入的试卷 ID，可为 null
     * @return 解析后的扫描意图
     */
    public static Resolution resolve(String mode, byte[] firstImage, Long paperId) {
        Long qrPaperId = PaperQrDecoder.decodePaperId(firstImage);
        if (qrPaperId != null) {
            return new Resolution(false, qrPaperId, true);
        }
        boolean anyMode = !"SELF".equalsIgnoreCase(mode);
        return new Resolution(anyMode, paperId, false);
    }

    /**
     * 解析结果。
     *
     * @param anyMode 是否按任意卷处理（false 表示内置卷 SELF）
     * @param paperId 实际使用的试卷 ID
     * @param fromQr  是否由整卷二维码识别得到
     */
    public record Resolution(boolean anyMode, Long paperId, boolean fromQr) {
    }
}