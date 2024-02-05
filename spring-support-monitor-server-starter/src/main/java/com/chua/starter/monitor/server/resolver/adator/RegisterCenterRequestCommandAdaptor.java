package com.chua.starter.monitor.server.resolver.adator;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.properties.MonitorProtocolProperties;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.service.ServiceInstance;

import javax.annotation.Resource;
import java.util.List;

/**
 * config-subscribe命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("request")
public class RegisterCenterRequestCommandAdaptor implements CommandAdaptor{


    @Resource
    private MonitorServerFactory monitorServerFactory;

    @Resource
    private MonitorProtocolProperties monitorProtocolProperties;

    @Override
    public BootResponse resolve(BootRequest request) {
        String appName = request.getAppName();
        if(StringUtils.isEmpty(appName)) {
            return BootResponse.empty();
        }
        List<MonitorRequest> heart = monitorServerFactory.getHeart(appName);
        if(CollectionUtils.isEmpty(heart)) {
            return BootResponse.empty();
        }
        Robin robin = ServiceProvider.of(Robin.class).getNewExtension(monitorProtocolProperties.getBalance());

        MonitorRequest request1 = null;
        if(null == robin) {
            request1 = heart.get(0);
        } else {
            robin.clear();
            for (MonitorRequest monitorRequest : heart) {
                Node node = new Node();
                node.setContent(monitorRequest);
                robin.addNode(node);
            }
            request1 = robin.selectNode().getValue(MonitorRequest.class);
        }

        ServiceInstance serviceInstance1 = new ServiceInstance();
        serviceInstance1.setProfile(request1.getProfile());
        serviceInstance1.setPort(Integer.parseInt(request1.getServerPort()));
        serviceInstance1.setName(request1.getAppName());
        serviceInstance1.setHost(request1.getServerHost());

        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder()
                        .commandType(CommandType.RESPONSE)
                        .content(Json.toJson(serviceInstance1))
                        .build())
                .build();
    }
}
