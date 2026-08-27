package com.chua.payment.support.service;

import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.vo.MerchantChannelVO;
import com.chua.payment.support.vo.PaymentMethodGuideVO;

import java.util.List;

/**
 * 商户渠道服务接口，定义支付渠道的增删改查、启用/禁用及加密操作契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface MerchantChannelService {

    MerchantChannelVO createChannel(ChannelConfigDTO dto);

    MerchantChannelVO updateChannel(Long id, ChannelConfigDTO dto);

    MerchantChannelVO getChannel(Long id);

    List<MerchantChannelVO> listChannels(Long merchantId, String channelType, Integer status);

    boolean enableChannel(Long id);

    boolean disableChannel(Long id);

    boolean deleteChannel(Long id);

    List<PaymentMethodGuideVO> listCatalog();

    String encryptApiKey(String apiKey);

    String decryptApiKey(String encryptedKey);

    /**
     * 获取商户的钱包渠道
     */
    MerchantChannel getWalletChannel(Long merchantId);
}

