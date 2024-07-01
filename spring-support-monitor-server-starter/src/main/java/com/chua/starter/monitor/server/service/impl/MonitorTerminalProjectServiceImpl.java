package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.channel.Channel;
import com.chua.common.support.utils.StringUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.ssh.support.ssh.SshClient;
import com.chua.starter.monitor.server.entity.MonitorTerminalProject;
import com.chua.starter.monitor.server.mapper.MonitorTerminalProjectMapper;
import com.chua.starter.monitor.server.service.MonitorTerminalProjectService;
import com.chua.starter.monitor.server.service.MonitorTerminalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author CH
 * @since 2024/6/19
 */
@Service
public class
MonitorTerminalProjectServiceImpl extends ServiceImpl<MonitorTerminalProjectMapper, MonitorTerminalProject> implements MonitorTerminalProjectService {

    @Resource
    private MonitorTerminalService monitorTerminalService;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;

    @Override
    public ReturnResult<Boolean> runStartScript(MonitorTerminalProject monitorTerminalProject) {
        SshClient sshClient = monitorTerminalService.getClient(monitorTerminalProject.getTerminalId() + "");
        if (null == sshClient) {
            return ReturnResult.illegal("会话未开启");
        }

        String startScript = getStartScript(monitorTerminalProject);
        if (null == startScript) {
            return ReturnResult.illegal("启动脚本为空");
        }


        Channel openChannel = sshClient.getSession().openChannel(String.valueOf(monitorTerminalProject.getTerminalId()), "exec");
        return ReturnResult.of(openChannel.execute(startScript, 1000 * 5));
    }

    /**
     * 获取启动脚本
     *
     * @param monitorTerminalProject 监控项目
     * @return 脚本
     */
    private String getStartScript(MonitorTerminalProject monitorTerminalProject) {
        String terminalProjectStartScript = monitorTerminalProject.getTerminalProjectStartScript();
        if (StringUtils.isBlank(terminalProjectStartScript)) {
            return null;
        }

        if (terminalProjectStartScript.startsWith("/")) {
            return terminalProjectStartScript;
        }
        return monitorTerminalProject.getTerminalProjectPath() + "/" + terminalProjectStartScript;
    }

    @Override
    public ReturnResult<Boolean> runStopScript(MonitorTerminalProject monitorTerminalProject) {
        SshClient sshClient = monitorTerminalService.getClient(monitorTerminalProject.getTerminalId() + "");
        if (null == sshClient) {
            return ReturnResult.illegal("会话未开启");
        }

        String stopScript = getStopScript(monitorTerminalProject);
        if (null == stopScript) {
            return ReturnResult.illegal("启动脚本为空");
        }
        Channel channel = sshClient.getSession().openChannel(String.valueOf(monitorTerminalProject.getTerminalId()), "exec");
        return ReturnResult.of(channel.execute(stopScript, 1000 * 5));
    }

    @Override
    public ReturnResult<Boolean> logStart(MonitorTerminalProject monitorTerminalProject, String event) {
        SshClient sshClient = monitorTerminalService.getClient(monitorTerminalProject.getTerminalId() + "");
        if (null == sshClient) {
            return ReturnResult.illegal("会话未开启");
        }

        String logScript = getLogScript(monitorTerminalProject);
        if (null == logScript) {
            return ReturnResult.illegal("日志文件不存在");
        }

        String logEventId = getLogEventId(monitorTerminalProject);
        Channel channel = sshClient.getSession().openChannel(logEventId, "exec");

        channel.setListener(s -> socketSessionTemplate.send(event, s));
        channel.execute("tail -f " + logScript, 1000);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<Boolean> logStop(MonitorTerminalProject monitorTerminalProject) {
        SshClient sshClient = monitorTerminalService.getClient(monitorTerminalProject.getTerminalId() + "");
        if (null == sshClient) {
            return ReturnResult.illegal("会话未开启");
        }

        String logScript = getLogScript(monitorTerminalProject);
        if (null == logScript) {
            return ReturnResult.illegal("日志文件不存在");
        }

        String logEventId = getLogEventId(monitorTerminalProject);
        sshClient.getSession().closeChannel(logEventId);
        return ReturnResult.success();
    }

    private String getLogEventId(MonitorTerminalProject monitorTerminalProject) {
        return "terminal-log-" + monitorTerminalProject.getTerminalId();
    }

    /**
     * 获取启动脚本
     *
     * @param monitorTerminalProject 监控项目
     * @return 脚本
     */
    private String getLogScript(MonitorTerminalProject monitorTerminalProject) {
        String terminalProjectLog = monitorTerminalProject.getTerminalProjectLog();
        if (StringUtils.isBlank(terminalProjectLog)) {
            return null;
        }

        if (terminalProjectLog.startsWith("/")) {
            return terminalProjectLog;
        }
        return monitorTerminalProject.getTerminalProjectPath() + "/" + terminalProjectLog;
    }

    /**
     * 获取启动脚本
     *
     * @param monitorTerminalProject 监控项目
     * @return 脚本
     */
    private String getStopScript(MonitorTerminalProject monitorTerminalProject) {
        String terminalProjectEndScript = monitorTerminalProject.getTerminalProjectEndScript();
        if (StringUtils.isBlank(terminalProjectEndScript)) {
            return null;
        }

        if (terminalProjectEndScript.startsWith("/")) {
            return terminalProjectEndScript;
        }
        return monitorTerminalProject.getTerminalProjectPath() + "/" + terminalProjectEndScript;
    }
}
