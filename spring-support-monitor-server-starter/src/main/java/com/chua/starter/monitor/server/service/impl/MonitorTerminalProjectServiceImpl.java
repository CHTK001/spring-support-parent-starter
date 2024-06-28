package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.function.SafeConsumer;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.utils.StringUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.ssh.support.ssh.SshSession;
import com.chua.starter.monitor.server.entity.MonitorTerminalProject;
import com.chua.starter.monitor.server.mapper.MonitorTerminalProjectMapper;
import com.chua.starter.monitor.server.service.MonitorTerminalProjectService;
import com.chua.starter.monitor.server.service.MonitorTerminalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
/**
 *
 *
 * @since 2024/6/19 
 * @author CH
 */
@Service
public class
MonitorTerminalProjectServiceImpl extends ServiceImpl<MonitorTerminalProjectMapper, MonitorTerminalProject> implements MonitorTerminalProjectService{

    @Resource
    private MonitorTerminalService monitorTerminalService;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public ReturnResult<Boolean> runStartScript(MonitorTerminalProject monitorTerminalProject) {
        Session session = monitorTerminalService.getSession(monitorTerminalProject.getTerminalId() + "");
        if(null == session) {
            return ReturnResult.illegal("会话未开启");
        }

        String startScript = getStartScript(monitorTerminalProject);
        if(null == startScript) {
            return ReturnResult.illegal("启动脚本为空");
        }

        if(session instanceof SshSession terminalSession) {
            return ReturnResult.of(terminalSession.execute(startScript, 1000 * 5));
        }
        return null;
    }

    /**
     * 获取启动脚本
     * @param monitorTerminalProject 监控项目
     * @return 脚本
     */
    private String getStartScript(MonitorTerminalProject monitorTerminalProject) {
        String terminalProjectStartScript = monitorTerminalProject.getTerminalProjectStartScript();
        if(StringUtils.isBlank(terminalProjectStartScript)) {
            return null;
        }

        if(terminalProjectStartScript.startsWith("/")) {
            return terminalProjectStartScript;
        }
        return monitorTerminalProject.getTerminalProjectPath() + "/" + terminalProjectStartScript;
    }

    @Override
    public ReturnResult<Boolean> runStopScript(MonitorTerminalProject monitorTerminalProject) {
        Session session = monitorTerminalService.getSession(monitorTerminalProject.getTerminalId() + "");
        if(null == session) {
            return ReturnResult.illegal("会话未开启");
        }

        String stopScript = getStopScript(monitorTerminalProject);
        if(null == stopScript) {
            return ReturnResult.illegal("启动脚本为空");
        }

        if(session instanceof SshSession terminalSession) {
            return ReturnResult.of(terminalSession.execute(stopScript, 1000 * 5));
        }
        return null;
    }

    @Override
    public ReturnResult<Boolean> logStart(MonitorTerminalProject monitorTerminalProject, String event) {
        Session session = monitorTerminalService.getSession(monitorTerminalProject.getTerminalId() + "");
        if(null == session) {
            return ReturnResult.illegal("会话未开启");
        }

        String logScript = getLogScript(monitorTerminalProject);
        if(null == logScript) {
            return ReturnResult.illegal("日志文件不存在");
        }

        if(session instanceof SshSession terminalSession) {
            String logEventId = getLogEventId(monitorTerminalProject);
            SshSession sshSession = terminalSession.getSshSession().getOrCreateSession(logEventId);
            sshSession.setListener(new SafeConsumer<String>() {
                @Override
                public void safeAccept(String s) throws Throwable {
                    socketSessionTemplate.send(event, s);
                }
            });
            sshSession.executeQuery("tail -f " + logScript);
            return ReturnResult.success();
        }
        return null;
    }

    @Override
    public ReturnResult<Boolean> logStop(MonitorTerminalProject monitorTerminalProject) {
        Session session = monitorTerminalService.getSession(monitorTerminalProject.getTerminalId() + "");
        if(null == session) {
            return ReturnResult.illegal("会话未开启");
        }

        String logScript = getLogScript(monitorTerminalProject);
        if(null == logScript) {
            return ReturnResult.illegal("日志文件不存在");
        }

        if(session instanceof SshSession terminalSession) {
            String logEventId = getLogEventId(monitorTerminalProject);
            SshSession sshSession = terminalSession.getSshSession().getOrCreateSession(logEventId);
            try {
                terminalSession.getSshSession().removeSession(logEventId);
                sshSession.close();
            } catch (Exception e) {
            }
            return ReturnResult.success();
        }
        return null;
    }

    private String getLogEventId(MonitorTerminalProject monitorTerminalProject) {
        return "terminal-log-" + monitorTerminalProject.getTerminalId();
    }

    /**
     * 获取启动脚本
     * @param monitorTerminalProject 监控项目
     * @return 脚本
     */
    private String getLogScript(MonitorTerminalProject monitorTerminalProject) {
        String terminalProjectLog = monitorTerminalProject.getTerminalProjectLog();
        if(StringUtils.isBlank(terminalProjectLog)) {
            return null;
        }

        if(terminalProjectLog.startsWith("/")) {
            return terminalProjectLog;
        }
        return monitorTerminalProject.getTerminalProjectPath() + "/" + terminalProjectLog;
    }
    /**
     * 获取启动脚本
     * @param monitorTerminalProject 监控项目
     * @return 脚本
     */
    private String getStopScript(MonitorTerminalProject monitorTerminalProject) {
        String terminalProjectEndScript = monitorTerminalProject.getTerminalProjectEndScript();
        if(StringUtils.isBlank(terminalProjectEndScript)) {
            return null;
        }

        if(terminalProjectEndScript.startsWith("/")) {
            return terminalProjectEndScript;
        }
        return monitorTerminalProject.getTerminalProjectPath() + "/" + terminalProjectEndScript;
    }
}
