package com.chua.starter.unified.server.support.adator;


import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;

/**
 * 默认命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@SpiDefault
public class DefaultCommandAdaptor implements ConfigCommandAdaptor, ExecutorCommandAdaptor{
    @Override
    public BootResponse resolve(BootRequest request) {
        return null;
    }
}
