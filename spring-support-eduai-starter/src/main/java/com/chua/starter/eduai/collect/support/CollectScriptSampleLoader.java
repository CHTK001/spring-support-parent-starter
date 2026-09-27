package com.chua.starter.eduai.collect.support;

import com.chua.starter.eduai.model.vo.CollectScriptSampleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置 Groovy 采集脚本样例加载器：读取 classpath 下 {@code collect/scripts/*.groovy}，
 * 并解析脚本首部 {@code // 名称:} {@code // 学科:} {@code // 说明:} {@code // 配置:} 元数据。
 * <p>
 * 第三方新增样例只需在自己的 jar 内放入同名目录的 .groovy 文件（classpath*: 会一并扫到）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class CollectScriptSampleLoader {

    /**
     * 样例脚本扫描位置
     */
    private static final String SCRIPT_PATTERN = "classpath*:collect/scripts/*.groovy";

    /**
     * 首部元数据注释
     */
    private static final Pattern META = Pattern.compile("^//\\s*(名称|学科|说明|配置)\\s*[:：]\\s*(.*)$");

    private static final List<CollectScriptSampleVO> SAMPLES = doLoad();

    private CollectScriptSampleLoader() {
    }

    /**
     * 全部内置样例（按编码排序，进程内缓存）。
     *
     * @return 样例列表
     */
    public static List<CollectScriptSampleVO> samples() {
        return SAMPLES;
    }

    private static List<CollectScriptSampleVO> doLoad() {
        List<CollectScriptSampleVO> samples = new ArrayList<>();
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                    CollectScriptSampleLoader.class.getClassLoader());
            for (Resource resource : resolver.getResources(SCRIPT_PATTERN)) {
                CollectScriptSampleVO sample = read(resource);
                if (sample != null) {
                    samples.add(sample);
                }
            }
            samples.sort(Comparator.comparing(CollectScriptSampleVO::getCode));
        } catch (Exception e) {
            log.error("采集脚本样例加载失败", e);
        }
        log.info("内置采集脚本样例已加载: {}", samples.stream().map(CollectScriptSampleVO::getCode).toList());
        return samples;
    }

    private static CollectScriptSampleVO read(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".groovy")) {
            return null;
        }
        String content;
        try (InputStream in = resource.getInputStream()) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("采集脚本样例读取失败: {}", filename, e);
            return null;
        }
        CollectScriptSampleVO sample = new CollectScriptSampleVO();
        sample.setCode(filename.substring(0, filename.length() - ".groovy".length()));
        sample.setName(sample.getCode());
        sample.setContent(content);
        for (String line : content.split("\r?\n")) {
            if (!line.startsWith("//")) {
                break;
            }
            Matcher matcher = META.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String value = matcher.group(2).trim();
            switch (matcher.group(1)) {
                case "名称" -> sample.setName(value);
                case "学科" -> sample.setSubject(value);
                case "说明" -> sample.setDescription(value);
                case "配置" -> sample.setConfigHint(value);
                default -> {
                }
            }
        }
        return sample;
    }
}
