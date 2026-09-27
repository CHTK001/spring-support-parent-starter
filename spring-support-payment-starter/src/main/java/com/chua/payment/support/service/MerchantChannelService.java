package com.chua.payment.support.service;

import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.vo.MerchantChannelVO;
import com.chua.payment.support.vo.PaymentMethodGuideVO;

import java.util.List;

/**
 * 客户渠道服务接口，定义支付渠道的增删改查、启用、禁用及加解密操作契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface MerchantChannelService {

    /**
     * 创建渠道。
     * @param dto dto
     * @return 创建渠道的结果
     */
    MerchantChannelVO createChannel(ChannelConfigDTO dto);

    /**
     * 更新渠道。
     * @param id 标识
     * @param dto dto
     * @return 更新渠道的结果
     */
    MerchantChannelVO updateChannel(Long id, ChannelConfigDTO dto);

    /**
     * 获取渠道。
     * @param id 标识
     * @return 获取渠道的结果
     */
    MerchantChannelVO getChannel(Long id);

    /**
     * 列表渠道。
     * @param merchantId 商户标识
     * @param channelType 渠道类型
     * @param status 状态
     * @return 列表渠道的结果
     */
    List<MerchantChannelVO> listChannels(Long merchantId, String channelType, Integer status);

    /**
     * 启用渠道。
     * @param id 标识
     * @return 是否启用成功
     */
    boolean enableChannel(Long id);

    /**
     * 禁用渠道。
     * @param id 标识
     * @return 是否禁用成功
     */
    boolean disableChannel(Long id);

    /**
     * 删除渠道。
     * @param id 标识
     * @return 删除渠道的结果
     */
    boolean deleteChannel(Long id);

    /**
     * 列表支付方式目录。
     * @return 支付方式目录列表
     */
    List<PaymentMethodGuideVO> listCatalog();

    /**
     * 加密 apiKey。
     * @param apiKey apiKey
     */
    String encryptApiKey(String apiKey);

    /**
     * 解密 apiKey。
     * @param encryptedKey encryptedKey
     */
    String decryptApiKey(String encryptedKey);

    /**
     * 获取商户的钱包渠道。
     * @param merchantId 商户标识
     * @return 获取到的钱包渠道
     */
    MerchantChannel getWalletChannel(Long merchantId);
}
