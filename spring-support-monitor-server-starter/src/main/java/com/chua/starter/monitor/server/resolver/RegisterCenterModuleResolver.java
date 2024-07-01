package com.chua.starter.monitor.server.resolver;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.server.resolver.adator.CommandAdaptor;

/**
 * 配置模块解析器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("register_center")
public class RegisterCenterModuleResolver implements ModuleResolver{


    @Override
    public Response resolve(Request request) {
        JsonObject requestBody = request.getBody(JsonObject.class);
        CommandType commandType = requestBody.getEnum("commandType", CommandType.class);
        return ServiceProvider.of(CommandAdaptor.class).getNewExtension("register_center_" + commandType).resolve(request);
    }
}
