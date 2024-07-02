package com.chua.starter.monitor.server.resolver.adator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.request.BadResponse;
import com.chua.common.support.protocol.request.OkResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.entity.MonitorMybatis;
import com.chua.starter.monitor.server.request.ReportQuery;
import com.chua.starter.monitor.server.service.MonitorMybatisService;
import jakarta.annotation.Resource;

import java.util.List;


/**
 * config-subscribe命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("mybatis_subscribe")
public class MybatisSubscribeCommandAdaptor implements CommandAdaptor{


    @Resource
    private MonitorMybatisService monitorMybatisService;


    @Override
    public Response resolve(Request request, ReportQuery reportQuery) {
        if(null == reportQuery) {
            return new BadResponse(request, "content is empty");
        }

        List<MonitorMybatis> list = monitorMybatisService.list(Wrappers.<MonitorMybatis>lambdaQuery()
                .eq(MonitorMybatis::getMonitorMybatisProfile, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(
                        reportQuery.getProfileName()))
                .eq(MonitorMybatis::getMonitorMybatisStatus, 1)
                .in(StringUtils.isNotEmpty(reportQuery.getSubscribeAppName()), MonitorMybatis::getMonitorAppname,
                        Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(reportQuery.getSubscribeAppName()))
        );
        return new OkResponse(request, Json.toJson(list));
    }
}
