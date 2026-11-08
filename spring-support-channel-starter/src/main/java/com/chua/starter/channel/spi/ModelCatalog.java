package com.chua.starter.channel.spi;

import java.util.List;
import java.util.Map;

/**
 * 模型目录能力 SPI —— 在线拉取账号可用模型（仅 {@link ChannelKind#AI} 渠道适用）。
 *
 * <p>与 {@link FixedAccountProvider} 正交：AI 渠道用它把 provider 行下的模型清单同步到
 * {@code sys_ai_provider_model}，接码 / 收信渠道不需要。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ModelCatalog {

    /**
     * 拉取当前账号可用模型。
     *
     * @param credentials 当前凭据
     * @return 模型列表，每项含 {@code id} / {@code name}，可选 {@code contextWindow}；
     *         不支持在线拉取的渠道返回默认清单（可为空）
     * @throws Exception 拉取失败
     */
    List<Map<String, Object>> listModels(Map<String, Object> credentials) throws Exception;
}
