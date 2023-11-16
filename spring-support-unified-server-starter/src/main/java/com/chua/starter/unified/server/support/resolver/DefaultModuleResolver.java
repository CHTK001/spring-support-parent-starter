package com.chua.starter.unified.server.support.resolver;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;

/**
 * 命令解析器
 *
 * @author CH
 */
@SpiDefault
public class DefaultModuleResolver implements ModuleResolver{
    @Override
    public BootResponse resolve(BootRequest request) {
        return BootResponse.notSupport();
    }
}