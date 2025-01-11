package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.collection.Options;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.protocol.server.AuthService;
import com.chua.mica.support.server.MicaServer;
import com.chua.report.server.starter.mapper.MonitorMqttServerMapper;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.report.server.starter.entity.MonitorMqttServer;
import com.chua.report.server.starter.service.MonitorMqttServerService;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MonitorMqttServerServiceImpl extends ServiceImpl<MonitorMqttServerMapper, MonitorMqttServer> implements MonitorMqttServerService {

    final TransactionTemplate transactionTemplate;

    static final Map<Integer, MicaServer> MICA_SERVER_MAP = new ConcurrentReferenceHashMap<>();

    @Override
    public ReturnResult<Boolean> deleteFor(Integer id) {
        return ReturnResult.of(baseMapper.deleteById(id) > 0);
    }

    @Override
    public ReturnResult<Boolean> updateFor(MonitorMqttServer t) {
        return ReturnResult.of(baseMapper.updateById(t) > 0);
    }

    @Override
    public ReturnResult<MonitorMqttServer> saveFor(MonitorMqttServer t) {
        baseMapper.insert(t);
        return ReturnResult.ok(t);
    }

    @Override
    public ReturnPageResult<MonitorMqttServer> pageFor(Query<MonitorMqttServer> page, MonitorMqttServer entity) {
        return ReturnPageResultUtils.ok(
                baseMapper.selectPage(page.createPage(), Wrappers.<MonitorMqttServer>lambdaQuery())
        );
    }

    @Override
    public ReturnResult<Boolean> start(Integer monitorMqttId) {
        MonitorMqttServer monitorMqttServer = getById(monitorMqttId);
        if (null == monitorMqttServer) {
            return ReturnResult.error("服务不存在");
        }

        Integer monitorMqttServerStatus = monitorMqttServer.getMonitorMqttServerStatus();
        if (null != monitorMqttServerStatus && monitorMqttServerStatus == 1) {
            return ReturnResult.error("服务已启动");
        }

        MicaServer micaServer = MICA_SERVER_MAP.get(monitorMqttServer.getMonitorMqttServerPort());
        if (null != micaServer) {
            monitorMqttServer.setMonitorMqttServerStatus(1);
            updateById(monitorMqttServer);
            return ReturnResult.error("服务已启动");
        }


        return transactionTemplate.execute(it -> {
            MicaServer micaServer1 = new MicaServer(
                    ServerSetting.builder()
                            .port(monitorMqttServer.getMonitorMqttServerPort())
                            .host(monitorMqttServer.getMonitorMqttServerHost())
                            .addDefaultMapping(false)
                            .build());

            try {
                String monitorMqttServerUsername = monitorMqttServer.getMonitorMqttServerUsername();
                String monitorMqttServerPassword = monitorMqttServer.getMonitorMqttServerPassword();
                if(!ObjectUtils.isEmpty(monitorMqttServerUsername) && !ObjectUtils.isEmpty(monitorMqttServerPassword)) {
                    micaServer1.addDefinition(new AuthService() {
                        @Override
                        public boolean auth(String s, String s1, String s2) {
                            return monitorMqttServerUsername.equals(s1) && monitorMqttServerPassword.equals(s2);
                        }
                    });
                }
                micaServer1.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            MICA_SERVER_MAP.put(monitorMqttServer.getMonitorMqttServerPort(), micaServer1);
            monitorMqttServer.setMonitorMqttServerStatus(1);
            updateById(monitorMqttServer);
            return ReturnResult.ok();
        });
    }

    @Override
    public ReturnResult<Boolean> stop(Integer monitorMqttId) {
        MonitorMqttServer mqttServer = getById(monitorMqttId);
        if (null == mqttServer) {
            return ReturnResult.error("服务不存在");
        }
        Integer monitorMqttServerStatus = mqttServer.getMonitorMqttServerStatus();
        if (null == monitorMqttServerStatus || monitorMqttServerStatus == 0) {
            return ReturnResult.error("服务未启动");
        }

        MicaServer micaServer = MICA_SERVER_MAP.get(mqttServer.getMonitorMqttServerPort());
        if (null == micaServer) {
            mqttServer.setMonitorMqttServerStatus(0);
            updateById(mqttServer);
            return ReturnResult.error("服务未启动");
        }


        return transactionTemplate.execute(it -> {
            try {
                micaServer.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            mqttServer.setMonitorMqttServerStatus(0);
            updateById(mqttServer);
            MICA_SERVER_MAP.remove(mqttServer.getMonitorMqttServerPort());
            return ReturnResult.ok();
        });
    }
}
