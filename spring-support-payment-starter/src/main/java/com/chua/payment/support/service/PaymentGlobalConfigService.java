package com.chua.payment.support.service;

import com.chua.payment.support.dto.PaymentGlobalConfigDTO;
import com.chua.payment.support.entity.PaymentGlobalConfig;
import com.chua.payment.support.vo.PaymentGlobalConfigVO;

/**
 * 支付全局配置服务
 */
public interface PaymentGlobalConfigService {

    PaymentGlobalConfigVO getConfig();

    PaymentGlobalConfig getConfigEntity();

    PaymentGlobalConfigVO saveOrUpdate(PaymentGlobalConfigDTO dto);
}
