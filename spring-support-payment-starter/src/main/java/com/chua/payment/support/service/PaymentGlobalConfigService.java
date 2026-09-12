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
     * 获取配置。
     * @return 获取配置的结果
     */
    PaymentGlobalConfigVO getConfig();

    /**
     * 获取配置实体。
     * @return 获取配置实体的结果
     */
    PaymentGlobalConfig getConfigEntity();

    /**
      * 保存或更新。
     * @param dto dto
     * @return 保存or更新的结果
     */
    PaymentGlobalConfigVO saveOrUpdate(PaymentGlobalConfigDTO dto);
}

