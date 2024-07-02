package com.chua.starter.monitor.server.controller;


import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.protocol.request.DefaultRequest;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.pojo.IpInstance;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import com.chua.starter.monitor.server.request.RemoteRequest;
import com.chua.starter.monitor.server.request.ReportQuery;
import com.chua.starter.monitor.server.resolver.ModuleResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * 报表控制器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@RequestMapping
@Tag(name = "上报数据接口")
@RestController
public class ReportController {

    @Resource
    private MonitorServerProperties monitorServerProperties;
    @Resource
    private MonitorServerFactory monitorServerFactory;

    @Resource
    private RemoteRequest remoteRequest;
    @PostMapping("/report")
    @Operation(summary = "上报数据")
    public ReturnResult<byte[]> home(@RequestBody byte[] body) {
        ReportQuery reportQuery = remoteRequest.getRequest(body);
        Request request = new DefaultRequest(null, null, remoteRequest.getCodec());
        if(null == reportQuery) {
            return ReturnResult.illegal("请求不能为空");
        }

        CommandType commandType = reportQuery.getCommandType();
        if(null == commandType) {
            return ReturnResult.illegal( "请求不能为空");
        }

        String moduleType = reportQuery.getModuleType();
        if(null == moduleType) {
            return ReturnResult.illegal( "请求不能为空");
        }

        String appName = reportQuery.getAppName();
        if(StringUtils.isBlank(appName)) {
            return ReturnResult.illegal( "appName不能为空");
        }

        try {
            Response response = ServiceProvider.of(ModuleResolver.class).getNewExtension(moduleType)
                    .resolve(request, reportQuery);

            return response.isSuccessful() ? ReturnResult.success(response.getBody()) : ReturnResult.illegal(response.message());
        } catch (Exception e) {
            return ReturnResult.illegal( "操作失败");
        }
    }

    /**
     * 添加数据
     *
     * @return 分页结果
     */
    @Operation(summary = "获取一段时间内客户端访问量")
    @GetMapping("instance/ip")
    public ReturnResult<List<IpInstance>> get(String appName, String serverAddress) {
        if (StringUtils.isEmpty(appName)) {
            return ReturnResult.illegal("应用不存在");
        }
        return ReturnResult.ok(monitorServerFactory.getIpInstance(appName, serverAddress));
    }
}
