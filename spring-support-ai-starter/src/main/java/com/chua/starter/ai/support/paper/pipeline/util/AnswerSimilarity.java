package com.chua.starter.ai.support.paper.pipeline.util;

/**
 * 答案相似度工具。
 *
 * <p>SELF 自有卷的标准答案在题库中，而扫描作答来自 OCR、必然存在个别错字
 * （实测「糊涂」被识成「湖涂」），若坚持严格相等，正确答案也会被判 0 分。
 * 本工具提供<b>归一化 + 对称最长公共子序列</b>的相似度，并叠加包含判定，
 * 同时容忍 OCR 错字与「答对但啰嗦」。</p>
 *
 * <p>原实现内联在 {@code EduMarkingServiceImpl} 中；抽出后由客观题容忍比对与
 * 判分兜底共用，避免 {@code ai} 层反向依赖 {@code service} 层。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class AnswerSimilarity {

    /**
     * 相似度判对阈值（0~1）。
     *
     * <p>取 0.7：能容忍 1~2 个字的识别误差（如「做了湖涂事」对「做了糊涂事」相似度 0.8），
     * 又不会把明显不同的作答判成正确。</p>
     */
    public static final double SIMILARITY_THRESHOLD = 0.7d;

    /**
     * 私有构造，防止实例化。
     */
    private AnswerSimilarity() {
    }

    /**
     * 答案相似度（0~1），用于容忍 OCR 个别错字与「答对但啰嗦」。
     *
     * <p>先归一化（去空白与中英标点、统一小写），再取<b>最长公共子序列</b>的对称比率：
     * {@code 2 * LCS / (len(a) + len(b))}。</p>
     *
     * <p>为什么不用编辑距离：编辑距离以「较长串」作分母，正确但啰嗦的作答会被压低。
     * 真实卷实测：「短文主要写丫小兴安岭一年四李的美丽景色」对标准
     * 「小兴安岭一年四季的美丽景色」（含 2 个 OCR 错字）编辑距离口径仅 0.6 判 0 分，
     * LCS 口径 0.77 可判满分；「做工湖涂事」对「做了糊涂事」也从 0.6 升到 0.8。
     * LCS 对插入/删减同样宽容，且是<b>对称量</b>（与参数顺序无关）。</p>
     *
     * @param standard 标准答案
     * @param student  学生作答（可能含 OCR 错字）
     * @return 相似度；任一侧为空返回 0
     */
    public static double similarity(String standard, String student) {
        var a = normalizeText(standard);
        var b = normalizeText(student);
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        if (a.equals(b)) {
            return 1d;
        }
        // 包含判定：学生作答完整包含标准答案即算对。
        // 否则 LCS 的对称分母会惩罚「答对但啰嗦」—— 实测把阅读题的子项 (1)(2) 并入作答后，
        // 13 字标准答案 vs 30 字作答只剩 0.6，正确作答被判 0 分。
        // 门槛取 3 字：避免把「A」对「AB」、「对」对「对错」这类短答案误判为包含。
        if (a.length() >= 3 && b.contains(a)) {
            return 1d;
        }
        int lcs = lcsLength(a, b);
        return 2d * lcs / (a.length() + b.length());
    }

    /**
     * 归一化文本：去掉空白与中英文标点，统一小写。
     *
     * @param value 原文本
     * @return 归一化文本
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。、；：？！（）〔〕《》〈〉“”‘’·—…￥【】]", "");
    }

    /**
     * 最长公共子序列长度（字符级，滚动数组实现）。
     *
     * @param a 串 a
     * @param b 串 b
     * @return LCS 长度
     */
    private static int lcsLength(String a, String b) {
        var prev = new int[b.length() + 1];
        var curr = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            var swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }
}