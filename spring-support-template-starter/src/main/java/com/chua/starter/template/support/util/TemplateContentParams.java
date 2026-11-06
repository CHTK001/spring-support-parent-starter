package com.chua.starter.template.support.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板占位符解析
 *
 * <p>统一模板库里有两类内容都可能带占位符：本系统设计的模板与三方同步进来的
 * 消息模板（短信 / 邮件）。占位符写法与
 * {@code com.chua.common.support.task.message.MessagePush#sendTemplate} 的替换规则一致
 * ——{@code {{键}}}，因此这里解析出来的键可以直接作为 {@code MessageRequest.templateParams}
 * 的参数名交给发送方。</p>
 *
 * <p>放在模板库里而不是消息模块里：模板详情要回答的是「这个模板要哪些参数」，
 * 属于模板语义；消息模块只是恰好也按同一套规则替换。</p>
 *
 * @since 4.0.0.81
 * @author CH
 */
public final class TemplateContentParams {

    /** 占位符：{{ key }}，键允许字母数字下划线与点（如 order.no） */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

    private TemplateContentParams() {
    }

    /**
     * 取内容里的占位符参数名
     *
     * @param content 模板内容
     * @return 参数名列表（去重，保持首次出现的顺序）；内容为空时返回空列表
     */
    public static List<String> of(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(names);
    }
}
