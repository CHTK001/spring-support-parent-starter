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

    PaymentGlobalConfigVO getConfig();

    PaymentGlobalConfig getConfigEntity();

    PaymentGlobalConfigVO saveOrUpdate(PaymentGlobalConfigDTO dto);
}

