package com.chua.starter.eduai.collect.provider;

import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 粘贴文本采集源：配置 {text}，走 JSON→AI→正则三级提取。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi(value = "TEXT", order = 100)
public class TextQuestionSourceProvider extends AbstractTextExtractProvider {

    @Override
    public String type() {
        return "TEXT";
    }

    @Override
    public List<CollectedQuestion> fetch(QuestionSourceContext context) {
        String text = SourceConfigReader.text(context.getSourceConfig(), "text");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TEXT 来源配置缺少 text 内容");
        }
        List<CollectedQuestion> questions = extract(text);
        questions.forEach(q -> q.setSource("手动粘贴文本"));
        return questions;
    }
}
