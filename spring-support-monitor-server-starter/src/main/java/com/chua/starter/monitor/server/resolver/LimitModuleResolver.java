package com.chua.starter.monitor.server.resolver;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.server.request.ReportQuery;
import com.chua.starter.monitor.server.resolver.adator.CommandAdaptor;

/**
 * 配置模块解析器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("limit")
public class LimitModuleResolver implements ModuleResolver{


    @Override
    public Response resolve(Request request, ReportQuery reportQuery) {
        CommandType commandType = reportQuery.getCommandType();
        return ServiceProvider.of(CommandAdaptor.class).getNewExtension("limit_" + commandType).resolve(request, reportQuery);
    }
}
