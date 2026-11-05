// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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

    /**
     * 二维码携带学生/考试时，ANY 无前端入参也能自动绑定身份并归回内置卷。
     */
    @Test
    @DisplayName("ANY + 身份二维码 → 归内置卷并自动绑定学生/考试")
    void qrWithIdentity_shouldBindStudentAndExam() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("PAPER:9001;STUDENT:20260001;EXAM:88"), null, null, null);
        assertFalse(r.anyMode(), "二维码命中后应归为内置卷 SELF");
        assertEquals(9001L, r.paperId());
        assertEquals("20260001", r.studentNo(), "未传学生时应由二维码自动绑定");
        assertEquals(88L, r.examId(), "未传考试时应由二维码自动绑定");
        assertFalse(r.identityConflict());
    }

    /**
     * 前端传入学生与二维码学生一致时正常绑定，不触发替考告警。
     */
    @Test
    @DisplayName("前端学生与二维码学生一致 → 无替考告警")
    void matchingStudent_shouldNotConflict() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("PAPER:9001;STUDENT:20260001"), null, "20260001", null);
        assertEquals("20260001", r.studentNo());
        assertFalse(r.identityConflict(), "学生一致时不应触发替考告警");
    }

    /**
     * 前端传入学生与二维码学生不一致 → 触发替考告警。
     */
    @Test
    @DisplayName("前端学生与二维码学生不一致 → 替考告警")
    void mismatchedStudent_shouldConflict() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("PAPER:9001;STUDENT:20260001"), null, "20260002", null);
        assertTrue(r.identityConflict(), "提交学生与卷面学生不一致应标记替考");
        assertEquals("20260001", r.studentNo(), "冲突时学生以二维码卷面为准");
    }

    /**
     * 前端已传学生、二维码未携带学生 → 沿用前端学生，无冲突。
     */
    @Test
    @DisplayName("前端传学生、二维码无学生 → 沿用前端学生")
    void qrWithoutStudent_shouldKeepProvidedStudent() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("PAPER:9001"), null, "20260009", null);
        assertEquals("20260009", r.studentNo());
        assertFalse(r.identityConflict());
    }

    /**
     * 学生号大小写不同但内容相同 → 不误报替考（学号不区分大小写）。
     */
    @Test
    @DisplayName("学号大小写不同但内容相同 → 不误报替考")
    void sameStudentDifferentCase_shouldNotConflict() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("PAPER:9001;STUDENT:ABC2026"), null, "abc2026", null);
        assertFalse(r.identityConflict(), "大小写差异不应触发替考");
        assertEquals("ABC2026", r.studentNo(), "冲突场景外仍以二维码学号为准");
    }

    /**
     * 前端已传考试 ID、二维码也带考试 ID → 以手动传入为准（显式选择覆盖卷面）。
     */
    @Test
    @DisplayName("前端手动考试 ID → 优先于二维码考试 ID")
    void providedExamShouldOverrideQrExam() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("PAPER:9001;EXAM:88"), null, null, 99L);
        assertEquals(99L, r.examId(), "手动传入考试 ID 应优先于二维码");
        assertEquals(9001L, r.paperId());
        assertFalse(r.identityConflict());
    }

    /**
     * 二维码只携带身份段（无 PAPER）→ 外来卷仍保持 ANY，但学生/考试可自动绑定。
     */
    @Test
    @DisplayName("仅身份二维码（无 PAPER）→ 保持 ANY 且绑定学生/考试")
    void identityOnlyQr_shouldStayAnyAndBind() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("STUDENT:20260001;EXAM:88"), null, null, null);
        assertTrue(r.anyMode(), "无 PAPER 段不应改变任意卷模式");
        assertNull(r.paperId(), "无 PAPER 段时试卷归属为空");
        assertEquals("20260001", r.studentNo());
        assertEquals(88L, r.examId());
        assertFalse(r.identityConflict());
    }

    /**
     * 前端传入学生与二维码学生完全一致（大小写也一致）→ 继续以二维码学生为准，无冲突。
     */
    @Test
    @DisplayName("前端学生与二维码学生完全一致 → 以二维码为准、无冲突")
    void exactMatchingStudent_shouldUseQrStudent() throws Exception {
        MarkingScanIntentResolver.Resolution r = MarkingScanIntentResolver.resolve(
                "ANY", createQrPng("PAPER:9001;STUDENT:20260011"), null, "20260011", null);
        assertEquals("20260011", r.studentNo());
        assertFalse(r.identityConflict());
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
    }}