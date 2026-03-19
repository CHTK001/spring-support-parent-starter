package com.chua.payment.support.service;

import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.MerchantChannel;

import java.util.List;

/**
 * 商户渠道服务接口
 *
 * @author CH
 * @since 2026-03-18
 */
public interface MerchantChannelService {

    /**
     * 创建渠道配置
     */
    MerchantChannel createChannel(ChannelConfigDTO dto);

    /**
     * 更新渠道配置
     */
    MerchantChannel updateChannel(Long id, ChannelConfigDTO dto);

    /**
     * 查询渠道配置
     */
    MerchantChannel getChannel(Long id);

    /**
     * 查询商户的所有渠道
     */
    List<MerchantChannel> listChannels(Long merchantId);

    /**
     * 启用渠道
     */
    boolean enableChannel(Long id);

    /**
     * 禁用渠道
     */
    boolean disableChannel(Long id);

    /**
     * 加密存储API密钥
     */
    String encryptApiKey(String apiKey);

    /**
     * 解密API密钥
     */
    String decryptApiKey(String encryptedKey);
}
