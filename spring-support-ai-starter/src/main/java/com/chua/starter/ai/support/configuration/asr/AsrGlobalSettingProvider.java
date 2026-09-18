package com.chua.starter.ai.support.configuration.asr;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
* 语音识别（ASR）模块全局设置提供者 SPI。
*
* <p>定义 {@code asr} 分组的全部配置项，由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
* 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。调用层通过
* {@link com.chua.starter.ai.support.configuration.client.AiCapabilityResolver} 按能力标签
* {@code asr} 统一解析该分组配置并创建 {@code AudioClient}（本地 whisper / 云端 ASR）。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Spi("asr")
public class AsrGlobalSettingProvider implements GlobalSettingProvider {

    /**
    * 配置分组标识
    */
    private static final String GROUP = "asr";

    /**
    * 分组显示名称
    */
    private static final String GROUP_NAME = "语音识别";

    /**
    * 配置分组标识。
    *
    * @return asr
    */
    @Override
    public String group() {
        return GROUP;
    }

    /**
    * 配置分组显示名称。
    *
    * @return 语音识别
    */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
    * 语音识别模块全部配置项。
    *
    * @return 配置项列表
    */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("provider", "whisper", "STRING", "ASR 服务商（whisper 本地 / openai / alibaba-asr 等）", 1),
                new SettingItem("provider_api_key", "", "STRING", "API Key（本地 whisper 无需密钥，云端 ASR 必填）", 2),
                new SettingItem("provider_base_url", "", "STRING", "API 基础地址（云端 ASR 使用，本地留空）", 3),
                new SettingItem("default_model", "whisper-tiny", "STRING", "ASR 默认模型（whisper-tiny / whisper-1 / paraformer-v2）", 4),
                new SettingItem("language", "zh", "STRING", "默认音频语言（ISO 639-1，如 zh / en，留空自动检测）", 5)
        );
    }
}
