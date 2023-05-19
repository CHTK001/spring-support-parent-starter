package com.chua.starter.common.support.key;

import com.chua.starter.common.support.constant.Constant;

/**
 * 密钥管理器
 * @author CH
 */
public class DelegateKeyManagerProvider implements KeyManagerProvider{

    @Override
    public String getKey(String binder) {
        return Constant.DEFAULT_SER;
    }
}
