package com.chua.starter.eduai.support.util;

/**
 * 阅卷扫码归类意图解析。
 *
 * <p>上传试卷时，第一张图片若带有整卷二维码（内容 {@code PAPER:{paperId}}），
 * 无论前端选择 ANY 还是 SELF、是否手动传了 paperId，都按二维码归回内置卷，
 * 后续链路以该试卷 ID + 识别题号从数据库取答案判分。
 * 二维码还可追加 {@code ;STUDENT:学号} 与 {@code ;EXAM:考试ID}，用于自动绑定学生、
 * 绑定考试并识别提交学生与卷面学生不一致的替考情况。</p>
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
        return resolve(mode, firstImage, paperId, null, null);
    }

    /**
     * 解析阅卷模式、试卷归属与学生/考试身份。
     *
     * @param mode       前端传入模式（SELF / ANY）
     * @param firstImage 第一张试卷图片字节流，可为 null
     * @param paperId    前端手动传入的试卷 ID，可为 null
     * @param studentId  前端手动传入的学生标识，可为 null
     * @param examId     前端手动传入的考试 ID，可为 null
     * @return 解析后的扫描意图
     */
    public static Resolution resolve(String mode, byte[] firstImage, Long paperId,
                                     String studentId, Long examId) {
        PaperQrDecoder.QrInfo qrInfo = PaperQrDecoder.decodeQrInfo(firstImage);
        Long qrPaperId = qrInfo == null ? null : qrInfo.paperId();
        boolean anyMode;
        Long resolvedPaperId;
        boolean fromQr;
        if (qrPaperId != null) {
            anyMode = false;
            resolvedPaperId = qrPaperId;
            fromQr = true;
        } else {
            anyMode = !"SELF".equalsIgnoreCase(mode);
            resolvedPaperId = paperId;
            fromQr = false;
        }
        String suppliedStudent = trimToNull(studentId);
        String qrStudent = qrInfo == null ? null : trimToNull(qrInfo.studentNo());
        // 卷面二维码优先：二维码命中学生时以卷面为准（身份冲突时成绩归属卷面学生）；
        // 二维码未携带学生时才沿用前端传入的学生。
        String resolvedStudent = qrStudent != null ? qrStudent : suppliedStudent;
        Long resolvedExam = examId != null ? examId : (qrInfo == null ? null : qrInfo.examId());
        boolean identityConflict = suppliedStudent != null
                && qrStudent != null
                && !suppliedStudent.equalsIgnoreCase(qrStudent);
        return new Resolution(anyMode, resolvedPaperId, fromQr, resolvedStudent, resolvedExam, identityConflict);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 解析结果。
     *
     * @param anyMode          是否按任意卷处理（false 表示内置卷 SELF）
     * @param paperId          实际使用的试卷 ID
     * @param fromQr           是否由整卷二维码识别得到
     * @param studentNo        实际使用的学生标识（二维码自动绑定或前端传入）
     * @param examId           实际使用的考试 ID
     * @param identityConflict 提交学生与卷面二维码学生不一致（替考告警）
     */
    public record Resolution(boolean anyMode, Long paperId, boolean fromQr,
                             String studentNo, Long examId, boolean identityConflict) {
    }
}