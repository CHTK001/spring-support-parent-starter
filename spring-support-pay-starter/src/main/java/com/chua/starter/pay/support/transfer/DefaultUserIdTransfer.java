package com.chua.starter.pay.support.transfer;

import com.chua.common.support.annotations.SpiDefault;

/**
 * 用户id转换
 *
 * @author CH
 */
@SpiDefault
public class DefaultUserIdTransfer implements UserIdTransfer{

    @Override
    public String transfer(String userId) {
        return userId;
    }
}
