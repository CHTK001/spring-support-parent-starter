package com.chua.starter.eduai.collect.provider;

import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.chua.common.support.spi.annotations.Spi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 服务器本地文件采集源：配置 {path}，仅允许 .json/.txt/.md，大小上限 10MB。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi(value = "FILE", order = 100)
public class FileQuestionSourceProvider extends AbstractTextExtractProvider {

    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024L;

    @Override
    public String type() {
        return "FILE";
    }

    @Override
    public List<CollectedQuestion> fetch(QuestionSourceContext context) {
        String path = SourceConfigReader.text(context.getSourceConfig(), "path");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("FILE 来源配置缺少 path");
        }
        Path file = Path.of(path);
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".md"))) {
            throw new IllegalArgumentException("仅支持 .json/.txt/.md 文件: " + path);
        }
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                throw new IllegalStateException("文件不存在或超过 10MB: " + path);
            }
            List<CollectedQuestion> questions = extract(Files.readString(file, StandardCharsets.UTF_8));
            questions.forEach(q -> q.setSource(path));
            return questions;
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + e.getMessage(), e);
        }
    }
}
