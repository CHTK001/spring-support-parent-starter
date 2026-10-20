package com.chua.starter.ai.support.configuration.tts;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 语音合成（TTS）模块全局设置提供者 SPI。
 *
 * <p>定义 {@code tts} 分组的全部配置项，由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。调用层通过
 * {@code sys_ai_user_setting} 的用户覆盖层优先、全局默认兜底解析该分组，
 * 再创建 {@code TextToAudioClient}（本地 vits / sherpa 等，或云端 TTS）。</p>
 *
 * <p><b>与既有 {@code ai.tts.*} 配置属性的关系</b>：后者是 Spring 的静态配置
 * （{@code application.yml}），只在 {@code ApiTtsController} 一处被读；本分组是
 * 「可在设置页改、按用户隔离」的运行时配置。两者并存不冲突：控制器优先读本分组，
 * 分组未配置时回落到 {@code ai.tts.*}，保证既有部署行为不变。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("tts")
public class TtsGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "tts";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "语音合成";

    /**
     * 配置分组标识。
     *
     * @return tts
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 语音合成
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 语音合成模块全部配置项。
     *
     * <p>字段口径与 {@code TextToAudioClientSetting} 一一对应：provider / 密钥 / 地址 /
     * 默认模型 / 发音人 / 语速 / 音频格式 / 语言。字段名刻意带 {@code tts} 前缀，
     * 以便前端 {@code requiredTypeFor} 按字段名判定「这是 TTS 模型」时能命中
     * （该判定是「字段名含 tts/speech_synthesis → tts 模型类型」）。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("provider", "", "STRING", "TTS 服务商（本地 onnx / sherpa / piper 等，留空则用配置文件 ai.tts.provider）", 1),
                new SettingItem("provider_api_key", "", "STRING", "API Key（本地 TTS 无需密钥，云端 TTS 必填）", 2),
                new SettingItem("provider_base_url", "", "STRING", "API 基础地址（云端 TTS 使用，本地留空）", 3),
                new SettingItem("default_model", "", "STRING", "TTS 默认模型（留空/auto=由所选引擎自动使用推荐档位，如 mms-tts-eng / vits-icefall-zh）", 4),
                new SettingItem("tts_voice", "", "STRING", "默认发音人（多说话人模型有效，如 SSB0005 / alloy）", 5),
                new SettingItem("tts_speed", "1.0", "NUMBER", "语速倍率（1.0 为原速，0.5 半速，2.0 倍速）", 6),
                new SettingItem("tts_format", "mp3", "STRING", "输出音频格式（wav / mp3 / ogg / flac / pcm）", 7),
                new SettingItem("language", "", "STRING", "合成语言（ISO 639-1，如 zh / en，留空由模型推断）", 8)
        );
    }
}
