package com.chua.starter.eduai.collect.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionScriptHelper;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.DefaultQuestionScriptHelper;
import com.chua.starter.eduai.collect.support.GroovyScriptCompiler;
import com.chua.starter.eduai.collect.support.JsonQuestionsParser;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import groovy.lang.Binding;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Groovy 脚本采集源：来源配置 {@code {script}} 内联脚本、{@code {scriptPath}} 服务器脚本文件，
 * 可选 {@code {timeoutSeconds}}。
 * <p>
 * 脚本可用变量：
 * <ul>
 *   <li>{@code context}——{@link QuestionSourceContext} 任务配置快照</li>
 *   <li>{@code collector}——{@link QuestionScriptHelper} 抓取/构造题目/读配置</li>
 *   <li>{@code log}——脚本日志</li>
 * </ul>
 * 脚本返回 {@code List<CollectedQuestion>}、单个 {@link CollectedQuestion}、题目 JSON 文本，
 * 或返回 {@code null}/{@code empty} 交由 {@code collector} 累积结果落库。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Spi(value = "GROOVY", order = 60)
public class GroovyQuestionSourceProvider implements QuestionSourceProvider {

    /**
     * 共享编译器（进程内缓存编译产物）
     */
    private static final GroovyScriptCompiler COMPILER = new GroovyScriptCompiler();

    /**
     * 脚本文件大小上限（字节）
     */
    private static final long MAX_SCRIPT_BYTES = 256 * 1024L;

    /**
     * 默认执行超时（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * 执行超时下界（秒）
     */
    private static final long MIN_TIMEOUT_SECONDS = 1L;

    /**
     * 执行超时上界（秒）
     */
    private static final long MAX_TIMEOUT_SECONDS = 600L;

    @Override
    public String type() {
        return "GROOVY";
    }

    @Override
    public List<CollectedQuestion> fetch(QuestionSourceContext context) {
        String source = readScript(context);
        QuestionScriptHelper helper = new DefaultQuestionScriptHelper(context, log);
        Object result = execute(source, context, helper, timeoutSeconds(context));
        return toQuestions(result, helper, context);
    }

    /**
     * 取脚本源码：内联 script 优先，其次 scriptPath 指向的服务器文件。
     */
    private String readScript(QuestionSourceContext context) {
        String script = SourceConfigReader.text(context.getSourceConfig(), "script");
        if (script != null && !script.isBlank()) {
            return script;
        }
        String path = SourceConfigReader.text(context.getSourceConfig(), "scriptPath");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("GROOVY 来源配置缺少 script 或 scriptPath");
        }
        Path file = Path.of(path);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_SCRIPT_BYTES) {
                throw new IllegalStateException("脚本文件不存在或超过 256KB: " + path);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取脚本文件失败: " + e.getMessage(), e);
        }
    }

    private long timeoutSeconds(QuestionSourceContext context) {
        String value = SourceConfigReader.text(context.getSourceConfig(), "timeoutSeconds");
        long seconds = DEFAULT_TIMEOUT_SECONDS;
        if (value != null && !value.isBlank()) {
            try {
                seconds = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                seconds = DEFAULT_TIMEOUT_SECONDS;
            }
        }
        return Math.min(MAX_TIMEOUT_SECONDS, Math.max(MIN_TIMEOUT_SECONDS, seconds));
    }

    /**
     * 编译并执行脚本：执行放在守护线程上，超时即判定本次采集失败，
     * 避免死循环脚本占死 {@code edu-collect-dispatch} 调度线程。
     *
     * @param source 脚本源码
     * @param context 采集上下文
     * @param helper 脚本工具对象
     * @param timeoutSeconds 执行超时（秒）
     * @return 脚本返回值
     */
    protected Object execute(String source, QuestionSourceContext context, QuestionScriptHelper helper,
                             long timeoutSeconds) {
        Class<?> type = COMPILER.compile(source);
        Binding binding = new Binding();
        binding.setVariable("context", context);
        binding.setVariable("collector", helper);
        binding.setVariable("log", log);
        Script script = InvokerHelper.createScript(type, binding);
        CompletableFuture<Object> finished = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                finished.complete(script.run());
            } catch (Throwable error) {
                finished.completeExceptionally(error);
            }
        }, "edu-collect-groovy-" + context.getTaskId());
        worker.setDaemon(true);
        worker.start();
        try {
            return finished.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            worker.interrupt();
            throw new IllegalStateException("Groovy 采集脚本执行超时（>" + timeoutSeconds + "s），已中断: 任务="
                    + context.getTaskName());
        } catch (InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Groovy 采集脚本执行被中断: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Groovy 采集脚本执行失败: " + cause, cause);
        }
    }

    private List<CollectedQuestion> toQuestions(Object result, QuestionScriptHelper helper,
                                                QuestionSourceContext context) {
        List<CollectedQuestion> questions = new ArrayList<>();
        if (result == null || (result instanceof List<?> list && list.isEmpty())) {
            questions.addAll(helper.getQuestions());
            enrichSource(questions, context);
            return questions;
        }
        if (result instanceof CollectedQuestion question) {
            questions.add(question);
        } else if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof CollectedQuestion question) {
                    questions.add(question);
                } else {
                    throw new IllegalStateException("脚本返回列表元素必须是 CollectedQuestion，实际: "
                            + (item == null ? "null" : item.getClass().getName()));
                }
            }
        } else if (result instanceof String json) {
            questions.addAll(JsonQuestionsParser.tryParse(json)
                    .orElseThrow(() -> new IllegalStateException("脚本返回的 JSON 中未找到题目数组")));
        } else {
            throw new IllegalStateException("脚本返回类型必须是 List<CollectedQuestion>、CollectedQuestion 或题目 JSON，实际: "
                    + result.getClass().getName());
        }
        enrichSource(questions, context);
        return questions;
    }

    /**
     * 脚本未标注来源时补一个可追溯标识，便于题库侧回溯采集任务。
     */
    private void enrichSource(List<CollectedQuestion> questions, QuestionSourceContext context) {
        for (CollectedQuestion question : questions) {
            if (question.getSource() == null || question.getSource().isBlank()) {
                question.setSource("groovy:" + context.getTaskName());
            }
        }
    }
}
