package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Nginx 配置模板（面向新人的起步模板库）。
 *
 * <p>每个模板都是一段可直接落盘的完整配置文本，附带中文说明与适用场景，
 * 让不熟悉 nginx 的人能先跑起来，再逐步改成自己需要的。</p>
 *
 * @param key       模板标识
 * @param title     模板名称
 * @param summary   一句话说明
 * @param scene     适用场景
 * @param difficulty 难度：easy / normal
 * @param content   配置文本
 * @param tips      使用提示，逐条展示
 * @param requiredFields 模板必填项的字段名，前端向导据此逐步收集
 * @author CH
 * @since 4.0.0.42
 */
public record NginxTemplateVO(
        String key,
        String title,
        String summary,
        String scene,
        String difficulty,
        String content,
        List<String> tips,
        List<String> requiredFields
) {

    /**
     * 规范构造器：集合组件做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * @param key       模板标识
     * @param title     模板名称
     * @param summary   一句话说明
     * @param scene     适用场景
     * @param difficulty 难度：easy / normal
     * @param content   配置文本
     * @param tips      使用提示，逐条展示
     * @param requiredFields 模板必填项的字段名，前端向导据此逐步收集
     */
    public NginxTemplateVO {
        tips = List.copyOf(Objects.requireNonNull(tips, "tips 不能为 null"));
        requiredFields = List.copyOf(Objects.requireNonNull(requiredFields, "requiredFields 不能为 null"));
    }
}
