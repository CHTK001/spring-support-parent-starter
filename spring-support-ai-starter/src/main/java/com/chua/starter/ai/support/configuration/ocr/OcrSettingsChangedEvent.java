package com.chua.starter.ai.support.configuration.ocr;

/**
 * OCR 全局设置变更事件，在 {@link com.chua.starter.ai.support.service.impl.SysAiGlobalSettingServiceImpl#replace}
 * 写入 ocr 分组值后发布给监听者；{@link com.chua.starter.ai.support.configuration.rag.RagClientManager}
 * 接收事件后重建 OCR 文本提取器，实现"全局设置实时生效"。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class OcrSettingsChangedEvent {

    /** 变更的分组名 */
    private final String group;

    public OcrSettingsChangedEvent(String group) {
        this.group = group;
    }

    public String getGroup() {
        return group;
    }
}
