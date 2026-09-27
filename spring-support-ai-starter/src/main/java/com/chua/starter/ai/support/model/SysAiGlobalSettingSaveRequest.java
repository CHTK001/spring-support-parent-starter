package com.chua.starter.ai.support.model;

import com.chua.starter.ai.support.entity.SysAiGlobalSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AI 全局设置保存请求模型。
 *
 * @param sysAiGlobalSettingGroup 设置分组
 * @param settings 设置项列表
 *
 * @since 4.0.0.42
 * @author CH
 */
public record SysAiGlobalSettingSaveRequest(
        String sysAiGlobalSettingGroup,
        List<SysAiGlobalSetting> settings
) {

    /**
     * 规范构造器：对设置项列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变；请求体元素允许为空，
     * 故采用允许空元素的不可变包装而非 {@code List.copyOf}。</p>
     *
     * @param settings 设置项列表
     */
    public SysAiGlobalSettingSaveRequest {
        settings = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(settings, "settings 不能为 null")));
    }
}
