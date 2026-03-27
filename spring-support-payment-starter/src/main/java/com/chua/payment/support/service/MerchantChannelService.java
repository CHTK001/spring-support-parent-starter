package com.chua.payment.support.service;

import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.vo.MerchantChannelVO;
import com.chua.payment.support.vo.PaymentMethodGuideVO;
import com.chua.payment.support.vo.ProviderSpiOptionVO;

import java.util.List;

/**
 * 商户渠道服务接口
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

    List<ProviderSpiOptionVO> listProviderOptions(String channelType);

    String encryptApiKey(String apiKey);

    String decryptApiKey(String encryptedKey);

    /**
     * 获取商户的钱包渠道
     */
    MerchantChannel getWalletChannel(Long merchantId);
}
