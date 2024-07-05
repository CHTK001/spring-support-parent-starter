package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.protocol.session.Session;
import com.chua.common.support.session.indicator.WIndicator;
import com.chua.common.support.utils.RegexUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.ssh.support.ssh.ExecChannel;
import com.chua.ssh.support.ssh.SshClient;
import com.chua.starter.monitor.server.entity.MonitorTerminal;
import com.chua.starter.monitor.server.entity.MonitorTerminalBase;
import com.chua.starter.monitor.server.mapper.MonitorTerminalBaseMapper;
import com.chua.starter.monitor.server.pojo.Last;
import com.chua.starter.monitor.server.service.IptablesService;
import com.chua.starter.monitor.server.service.MonitorTerminalBaseService;
import com.chua.starter.monitor.server.service.MonitorTerminalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import static com.chua.common.support.constant.NumberConstant.NUM_8;

/**
 *
 *
 * @since 2024/6/20
 * @author CH
 */
@Service
public class MonitorTerminalBaseServiceImpl extends ServiceImpl<MonitorTerminalBaseMapper, MonitorTerminalBase> implements MonitorTerminalBaseService{

    @Resource
    private MonitorTerminalService monitorTerminalService;

    @Resource
    private IptablesService iptablesService;

    @Override
    public ReturnResult<List<WIndicator>> w(MonitorTerminal monitorProxy) {
        SshClient sshClient = monitorTerminalService.getClient(String.valueOf(monitorProxy.getTerminalId()));
        if (null == sshClient) {
            return ReturnResult.illegal("会话未开启");
        }


        List<WIndicator> rs = new LinkedList<>();
        try {
            if ((null != sshClient)) {
                try (Session session = sshClient.createSession("exec-w")) {
                    ExecChannel channel = (ExecChannel) session.openChannel("exec-w", "exec");
                    try {
                        String execute = channel.execute("w -h", 4000);
                        String[] split = execute.split("\r\n");
                        for (String string : split) {
                            String[] split1 = string.split("\\s+");
                            if(split1.length != NUM_8) {
                                continue;
                            }
                            WIndicator wIndicator = new WIndicator();
                            wIndicator.setUser(split1[0]);
                            wIndicator.setFrom(split1[2]);
                            wIndicator.setLoginTime(split1[3]);
                            wIndicator.setIdle(split1[4]);
                            wIndicator.setJcpu(split1[5]);
                            wIndicator.setPcpu(split1[6]);
                            wIndicator.setWhat(split1[7]);

                            rs.add(wIndicator);
                        }
                    } finally {
                        session.closeChannel("exec-w");
                    }
                } finally {
                    sshClient.closeSession("exec-w");
                }
                return ReturnResult.of(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("终端未启动/不支持");
    }

    @Override
    public ReturnResult<List<Last>> last(MonitorTerminal monitorProxy) {
        SshClient sshClient = monitorTerminalService.getClient(String.valueOf(monitorProxy.getTerminalId()));
        if (null == sshClient) {
            return ReturnResult.illegal("会话未开启");
        }


        List<Last> rs = new LinkedList<>();
        try {
            if ((null != sshClient)) {
                Session session = sshClient.createSession("exec");
                ExecChannel channel = (ExecChannel) session.openChannel("exec", "exec");
                try {
                    String execute = channel.execute("last -F", 4000);
                    String[] split = execute.split("\n");
                    for (String string : split) {
                        if(StringUtils.isEmpty(string)) {
                            break;
                        }
                        String[] split1 = string.trim().split("\\s+");
                        Last last = new Last();
                        last.setUser(split1[0]);
                        last.setFrom(split1[2]);
                        GeoCity geoCity = iptablesService.getGeoCity(last.getFrom());
                        if(StringUtils.isNotEmpty(geoCity.getCity())) {
                            last.setCity(geoCity.getCity() + "-" + geoCity.getIsp());
                        }
                        String newString = string.substring(string.indexOf(last.getFrom()) + last.getFrom().length()).trim();
                        List<String> strings = RegexUtils.findAll(Pattern.compile("\\w+\\s+\\w+\\s+\\d+\\s+\\d+:+\\d+:\\d+\\s+\\d+"), newString, 0);
                        last.setLoginTime(DateTime.of(strings.get(0)).toStandard());
                        if(strings.size() != 1) {
                            last.setLogoutTime(DateTime.of(strings.get(1)).toStandard());
                        }

                        rs.add(last);
                    }
                } finally {
                    session.closeChannel("exec");
                }
                return ReturnResult.of(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("终端未启动/不支持");
    }
}
