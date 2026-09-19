package com.chua.starter.eduai.collect.provider;

import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.HttpFetchSupport;
import com.chua.starter.eduai.collect.support.JsonQuestionsParser;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.chua.common.support.spi.annotations.Spi;

import java.io.IOException;
import java.util.List;

/**
 * 第三方 API 采集源：配置 {apiUrl, method}，要求返回题目 JSON 数组（或 {data:[...]} 包装）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi(value = "API", order = 100)
public class ApiQuestionSourceProvider extends AbstractTextExtractProvider {

    @Override
    public String type() {
        return "API";
    }

    @Override
    public List<CollectedQuestion> fetch(QuestionSourceContext context) {
        String apiUrl = SourceConfigReader.text(context.getSourceConfig(), "apiUrl");
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("API 来源配置缺少 apiUrl");
        }
        try {
            List<CollectedQuestion> questions = JsonQuestionsParser.tryParse(HttpFetchSupport.get(apiUrl))
                    .orElseThrow(() -> new IllegalStateException("API 未返回可解析的题目 JSON 数组: " + apiUrl));
            questions.forEach(q -> q.setSource(apiUrl));
            return questions;
        } catch (IOException e) {
            throw new IllegalStateException("API 调用失败: " + e.getMessage(), e);
        }
    }
}
