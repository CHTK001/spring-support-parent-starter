package com.chua.payment.support.service;

import com.chua.payment.support.dto.PaymentGlobalConfigDTO;
import com.chua.payment.support.entity.PaymentGlobalConfig;
import com.chua.payment.support.vo.PaymentGlobalConfigVO;

/**
 * 支付全局配置服务接口，定义系统级支付参数的查询与保存契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentGlobalConfigService {

    /**
     * 获取支付全局配置。
     * @return 获取到的配置
     */
    PaymentGlobalConfigVO getConfig();

    /**
     * 获取支付全局配置实体。
     * @return 获取到的配置实体
     */
    PaymentGlobalConfig getConfigEntity();

    /**
     * 保存或更新支付全局配置。
     * @param dto dto
     * @return 保存或更新后的配置
     */
    PaymentGlobalConfigVO saveOrUpdate(PaymentGlobalConfigDTO dto);
}
