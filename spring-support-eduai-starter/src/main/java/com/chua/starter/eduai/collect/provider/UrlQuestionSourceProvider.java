package com.chua.starter.eduai.collect.provider;

import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.HttpFetchSupport;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.chua.common.support.spi.annotations.Spi;

import java.io.IOException;
import java.util.List;

/**
 * 网页 URL 采集源：配置 {url}。JSON 链接直解，普通网页 AI/正则提取。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi(value = "URL", order = 100)
public class UrlQuestionSourceProvider extends AbstractTextExtractProvider {

    @Override
    public String type() {
        return "URL";
    }

    @Override
    public List<CollectedQuestion> fetch(QuestionSourceContext context) {
        String url = SourceConfigReader.text(context.getSourceConfig(), "url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 来源配置缺少 url");
        }
        try {
            List<CollectedQuestion> questions = extract(HttpFetchSupport.get(url));
            questions.forEach(q -> q.setSource(url));
            return questions;
        } catch (IOException e) {
            throw new IllegalStateException("网页抓取失败: " + e.getMessage(), e);
        }
    }
}
