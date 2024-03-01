package com.chua.starter.monitor.server.terminal;

import com.chua.common.support.lang.language.Language;
import com.chua.common.support.session.Session;
import com.chua.common.support.session.query.ExecuteQuery;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.ssh.support.session.SshSession;
import com.chua.ssh.support.session.TerminalSession;
import com.chua.starter.monitor.server.entity.MonitorProject;
import com.chua.starter.monitor.server.entity.MonitorProjectVersion;
import com.chua.starter.monitor.server.service.MonitorProjectService;

/**
 * 启动脚本
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/28
 */
public class LogScript {
    private final MonitorProjectService monitorProjectService;
    private final SocketSessionTemplate socketSessionTemplate;

    public LogScript(MonitorProjectService monitorProjectService, SocketSessionTemplate socketSessionTemplate) {
        this.monitorProjectService = monitorProjectService;
        this.socketSessionTemplate = socketSessionTemplate;
    }

    public void run(MonitorProjectVersion monitorProjectVersion) {
        MonitorProject monitorProject = monitorProjectService.getById(monitorProjectVersion.getProjectId());
        if(null == monitorProjectVersion) {
            throw new RuntimeException(Language.newDefault().getString("${data.not-exist:数据不存在}"));
        }
        Session session = ServiceProvider.of(Session.class).getKeepExtension(monitorProject.getProjectId() + "terminal", "terminal", monitorProject.newDatabaseOptions());
        if(null != session) {
            TerminalSession terminalSession = (TerminalSession)session;
            if(!terminalSession.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(monitorProject.getProjectId()+ "");
                throw new RuntimeException("当前服务器不可达");
            }
            SshSession sshSession = terminalSession.getSshSession();
            session.setListener(message -> {
                socketSessionTemplate.send(monitorProjectVersion.getVersionId() + "terminal", message);
            });

            try {
                doStart(sshSession, monitorProjectVersion, monitorProject);
            } catch (Exception e) {
                throw new RuntimeException("脚本运行失败");
            }
        }
    }

    private void doStart(SshSession sshSession, MonitorProjectVersion monitorProjectVersion, MonitorProject monitorProject) throws Exception {
        sshSession.executeQuery(
                "tail -f  "
                        + getLogPath(monitorProjectVersion, monitorProject) + "\r", new ExecuteQuery());

    }

    private String getLogPath(MonitorProjectVersion monitorProjectVersion, MonitorProject monitorProject) {
        Integer versionLogPathPosition = monitorProjectVersion.getVersionLogPathPosition();
        return 0 == versionLogPathPosition ? FileUtils.normalize(monitorProject.getProjectProjectPath(), monitorProjectVersion.getVersionLog()) : monitorProjectVersion.getVersionLog();
    }

}
