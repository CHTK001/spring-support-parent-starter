package com.chua.starter.ai.support.model;

import com.chua.starter.ai.support.entity.SysAiGlobalSetting;

import java.util.List;

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
}
