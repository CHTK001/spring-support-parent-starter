package com.chua.starter.common.support.application;

import com.chua.common.support.utils.IdUtils;
import lombok.Data;

/**
 * 签名
 * @author CH
 * @since 2024/8/6
 */
@Data
public class Sign {

    /**
     * 签名
     */
    private String sign1 = IdUtils.createUlid();

    /**
     * 签名
     */
    private String sign2 = IdUtils.createUlid();
}
