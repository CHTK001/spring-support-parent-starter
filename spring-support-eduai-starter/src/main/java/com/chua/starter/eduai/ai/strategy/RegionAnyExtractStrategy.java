package com.chua.starter.eduai.ai.strategy;

import com.chua.starter.eduai.ai.EduPaperScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 按版面区域分块识别策略。
 *
 * <p>版面模型给出的每个区域（题干/选项/答题区/卷头等）连同落入该区域的文字块，各自作为一段
 * 文本单独送一次模型；相比整卷一次，模型上下文更小、更不易串题（同名句、跨题选项）。
 * 代价是调用量与耗时随区域数上升。</p>
 *
 * <p><b>降级</b>：拿不到版面区域、或按区域识别不出题目时，自动回退整卷一次识别，
 * 保证区域策略不会比整卷更差。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class RegionAnyExtractStrategy implements AnyExtractStrategy {

    @Override
    public String key() {
        return "region";
    }

    @Override
    public EduPaperScanner.AnyScan extract(Context context) {
        if (!StringUtils.hasText(context.text())) {
            return new EduPaperScanner.AnyScan(List.of(), null, null);
        }
        List<EduPaperScanner.LayoutBox> regions = context.layoutBoxes();
        List<EduPaperScanner.ScanBlock> blocks = context.blocks();
        if (regions == null || regions.isEmpty() || blocks == null || blocks.isEmpty()) {
            log.info("[EduScan] region 策略：无版面区域，回退整卷识别");
            return fallbackWhole(context);
        }

        // 区域按 (y, x) 排序：试卷阅读顺序，模型按顺序抽题时题号更不容易乱
        List<EduPaperScanner.LayoutBox> ordered = new ArrayList<>(regions);
        ordered.sort(Comparator.comparingDouble(EduPaperScanner.LayoutBox::y)
                .thenComparingDouble(EduPaperScanner.LayoutBox::x));

        List<EduPaperScanner.ScannedQuestion> merged = new ArrayList<>();
        String studentName = null;
        String studentNo = null;
        for (EduPaperScanner.LayoutBox region : ordered) {
            List<EduPaperScanner.ScanBlock> inner = blocksInside(region, blocks);
            if (inner.isEmpty()) {
                continue;
            }
            String regionText = joinText(inner);
            if (!StringUtils.hasText(regionText)) {
                continue;
            }
            try {
                EduPaperScanner.AnyScan scanned = context.modelExtract(regionText);
                merged.addAll(scanned.questions());
                if (studentName == null) {
                    studentName = scanned.studentName();
                }
                if (studentNo == null) {
                    studentNo = scanned.studentNo();
                }
            } catch (Exception e) {
                // 单个区域失败不影响其余区域：记录后继续；整体为空时由下面的回退兜住
                log.warn("[EduScan] region 策略：区域识别失败（{}），跳过该区域", e.getMessage());
            }
        }

        if (merged.isEmpty()) {
            log.info("[EduScan] region 策略：按区域未识别出题目，回退整卷识别");
            return fallbackWhole(context);
        }
        // 卷面学生优先用区域内模型给的；没有再用卷头正则兜底
        if (studentName == null && studentNo == null) {
            String[] student = context.studentByLines();
            studentName = student[0];
            studentNo = student[1];
        }
        log.info("[EduScan] region 策略：{}/{} 个区域并入 {} 道题",
                ordered.size(), ordered.size(), merged.size());
        return new EduPaperScanner.AnyScan(merged, studentName, studentNo);
    }

    /**
     * 回退整卷识别：复用整卷策略的链路，保证结果不劣于默认策略。
     *
     * @param context 识别上下文
     * @return 整卷识别结果
     */
    private EduPaperScanner.AnyScan fallbackWhole(Context context) {
        if (context.blocks() != null && !context.blocks().isEmpty()) {
            List<EduPaperScanner.ScannedQuestion> byLayout = context.layoutQuestions();
            if (!byLayout.isEmpty()) {
                List<EduPaperScanner.ScannedQuestion> withStem = context.fillStems(byLayout);
                String[] student = context.studentByLines();
                return new EduPaperScanner.AnyScan(
                        !withStem.isEmpty() ? withStem : byLayout, student[0], student[1]);
            }
        }
        try {
            EduPaperScanner.AnyScan byModel = context.modelExtract();
            if (!byModel.questions().isEmpty()) {
                return byModel;
            }
        } catch (Exception e) {
            log.warn("[EduScan] region 回退整卷：模型抽取失败，回退行规则: {}", e.getMessage());
        }
        String[] student = context.studentByLines();
        return new EduPaperScanner.AnyScan(context.ruleQuestions(), student[0], student[1]);
    }

    /**
     * 取中心点落在区域内的文字块。
     *
     * @param region 版面区域
     * @param blocks 文字块
     * @return 该区域内的文字块（保持原有顺序）
     */
    private static List<EduPaperScanner.ScanBlock> blocksInside(EduPaperScanner.LayoutBox region,
                                                                List<EduPaperScanner.ScanBlock> blocks) {
        List<EduPaperScanner.ScanBlock> out = new ArrayList<>();
        for (EduPaperScanner.ScanBlock block : blocks) {
            float cx = block.x() + block.width() / 2f;
            float cy = block.y() + block.height() / 2f;
            if (cx >= region.x() && cx <= region.x() + region.width()
                    && cy >= region.y() && cy <= region.y() + region.height()) {
                out.add(block);
            }
        }
        return out;
    }

    /**
     * 把块文本按阅读顺序拼成一段。
     *
     * @param blocks 文字块
     * @return 拼接文本
     */
    private static String joinText(List<EduPaperScanner.ScanBlock> blocks) {
        List<EduPaperScanner.ScanBlock> ordered = new ArrayList<>(blocks);
        ordered.sort(Comparator.comparingDouble(EduPaperScanner.ScanBlock::y)
                .thenComparingDouble(EduPaperScanner.ScanBlock::x));
        StringBuilder sb = new StringBuilder();
        for (EduPaperScanner.ScanBlock block : ordered) {
            if (StringUtils.hasText(block.text())) {
                sb.append(block.text().trim()).append('\n');
            }
        }
        return sb.toString();
    }
}