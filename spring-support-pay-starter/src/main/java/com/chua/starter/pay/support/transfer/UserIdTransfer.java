package com.chua.starter.pay.support.transfer;

/**
 * 用户id转换
 *
 * @author CH
 */
public interface UserIdTransfer {


    /**
     * 转换
     *
     * @param userId 用户id
     * @return 转换后的用户id
     */
    String transfer(String userId);
}
