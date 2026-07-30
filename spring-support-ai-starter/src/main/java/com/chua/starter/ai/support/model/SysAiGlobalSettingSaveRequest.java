package com.chua.starter.ai.support.model;

import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import lombok.Data;

import java.util.List;

@Data
public class SysAiGlobalSettingSaveRequest {

    private String sysAiGlobalSettingGroup;
    private List<SysAiGlobalSetting> settings;
}
