package com.chua.starter.ai.support.paper.pipeline.strategy;

import com.chua.starter.ai.support.paper.pipeline.PaperScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 整卷一次识别策略（默认）。
 *
 * <p>与历史行为完全一致，调用量最小：</p>
 * <ol>
 *   <li>版面规则切题（不调模型）→ 命中则用模型补一次题干；</li>
 *   <li>版面切不出时整卷调一次模型；</li>
 *   <li>模型也不可用才回退行规则（题干留空）。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class WholeTextAnyExtractStrategy implements AnyExtractStrategy {

    @Override
    public String key() {
        return "whole";
    }

    @Override
    public PaperScanner.AnyScan extract(Context context) {
        if (!StringUtils.hasText(context.text())) {
            return new PaperScanner.AnyScan(List.of(), null, null);
        }
        List<PaperScanner.ScanBlock> blocks = context.blocks();
        if (blocks != null && !blocks.isEmpty()) {
            List<PaperScanner.ScannedQuestion> byLayout = context.layoutQuestions();
            if (!byLayout.isEmpty()) {
                List<PaperScanner.ScannedQuestion> withStem = context.fillStems(byLayout);
                List<PaperScanner.ScannedQuestion> questions = !withStem.isEmpty() ? withStem : byLayout;
                String[] student = context.studentByLines();
                return new PaperScanner.AnyScan(questions, student[0], student[1]);
            }
        }
        try {
            PaperScanner.AnyScan byModel = context.modelExtract();
            if (!byModel.questions().isEmpty()) {
                return byModel;
            }
        } catch (Exception e) {
            log.warn("[EduScan] 模型抽取失败，回退行规则（题干留空）: {}", e.getMessage());
        }
        String[] student = context.studentByLines();
        return new PaperScanner.AnyScan(context.ruleQuestions(), student[0], student[1]);
    }
}