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

    /**
     * 创建通道。
     * @param dto dto
     * @return 创建通道的结果
     */
    MerchantChannelVO createChannel(ChannelConfigDTO dto);

    /**
     * 更新通道。
     * @param id 标识
     * @param dto dto
     * @return 更新通道的结果
     */
    MerchantChannelVO updateChannel(Long id, ChannelConfigDTO dto);

    /**
     * 获取通道。
     * @param id 标识
     * @return 获取通道的结果
     */
    MerchantChannelVO getChannel(Long id);

    /**
     * 列表通道。
     * @param merchantId merchantid
     * @param channelType 通道类型
     * @param status 状态
     * @return 列表通道的结果
     */
    List<MerchantChannelVO> listChannels(Long merchantId, String channelType, Integer status);

    /**
     * enable通道。
     * @param id 标识
     * @return enable通道的结果
     */
    boolean enableChannel(Long id);

    /**
     * disable通道。
     * @param id 标识
     * @return disable通道的结果
     */
    boolean disableChannel(Long id);

    /**
     * 删除通道。
     * @param id 标识
     * @return 删除通道的结果
     */
    boolean deleteChannel(Long id);

    /**
     * 列表catalog。
     * @return 列表catalog的结果
     */
    List<PaymentMethodGuideVO> listCatalog();

    /**
     * encryptapi钥匙。
     * @param apiKey api钥匙
     * @return encryptapi钥匙的结果
     */
    String encryptApiKey(String apiKey);

    /**
     * decryptapi钥匙。
     * @param encryptedKey encrypted钥匙
     * @return decryptapi钥匙的结果
     */
    String decryptApiKey(String encryptedKey);

    /**
     * 获取商户的钱包渠道
     * @param merchantId merchantid
     * @return 获取wallet通道的结果
     */
    MerchantChannel getWalletChannel(Long merchantId);
}

