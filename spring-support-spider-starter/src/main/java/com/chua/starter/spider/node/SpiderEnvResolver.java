package com.chua.starter.spider.node;

import com.chua.starter.spider.service.SpiderEnvService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量模板解析器。
 *
 * <p>将 {@code {{env.KEY}}} 占位符替换为 {@link SpiderEnvService#mapEnabled()}
 * 启用的环境变量值。供爬虫节点在 URL、请求头、提取表达式中等位置使用。</p>
 *
 * <p>解析规则：</p>
 * <ul>
 *   <li>占位符形如 {@code {{env.COOKIE}}}，大小写敏感匹配 KEY</li>
 *   <li>未匹配到对应环境变量时，替换为空字符串</li>
 *   <li>不包含占位符的模板原样返回</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Component
public class SpiderEnvResolver {

    /**
     * 环境变量占位符前缀
     */
    private static final String ENV_PREFIX = "{{env.";

    /**
     * 环境变量占位符后缀
     */
    private static final String ENV_SUFFIX = "}}";

    /**
     * 环境变量占位符正则，分组 1 为变量 Key
     */
    private static final String ENV_PATTERN = "\\{\\{env\\.([^}]+)\\}\\}";

    /**
     * 环境变量服务
     */
    private final SpiderEnvService envService;

    /**
     * 通过构造器注入环境变量服务。
     *
     * @param envService 环境变量服务
     */
    public SpiderEnvResolver(SpiderEnvService envService) {
        this.envService = envService;
    }

    /**
     * 解析模板中的环境变量占位符。
     *
     * <p>一次性加载当前启用的环境变量映射，逐个替换模板中的占位符。</p>
     *
     * @param template 原始模板字符串
     * @return 替换后的字符串，模板为 空 时返回空字符串
     */
    public String resolve(String template) {
        if (template == null || template.isEmpty()) {
            return template != null ? template : "";
        }
        if (!template.contains(ENV_PREFIX)) {
            return template;
        }
        Map<String, String> values = envService.mapEnabled();
        Pattern pattern = Pattern.compile(ENV_PATTERN);
        Matcher matcher = pattern.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.getOrDefault(key, "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
