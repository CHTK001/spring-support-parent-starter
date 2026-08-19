package com.chua.starter.ai.support.model;

import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import lombok.Data;

import java.util.List;

/**
 * AI 全局设置保存请求模型。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class SysAiGlobalSettingSaveRequest {

    /**
     * 设置分组
     */
    private String sysAiGlobalSettingGroup;

    /**
     * 设置项列表
     */
    private List<SysAiGlobalSetting> settings;
}
