package com.chua.starter.pay.support.properties;

import com.chua.common.support.utils.IdUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付通知
 *
 * @author CH
 * @since 2025/1/3
 */
@Data
@ConfigurationProperties(prefix = PayProperties.PRE, ignoreInvalidFields = true)
public class PayProperties {

    public static final String PRE = "plugin.pay";

    /**
     * 是否分表
     */
    private boolean tables;

}
