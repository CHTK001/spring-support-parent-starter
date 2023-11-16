package com.chua.starter.unified.server.support.adator;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;

/**
 * config-Register命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("Register")
public class ConfigRegisterCommandAdaptor implements ConfigCommandAdaptor{
    @Override
    public BootResponse resolve(BootRequest request) {
        return null;
    }
}
