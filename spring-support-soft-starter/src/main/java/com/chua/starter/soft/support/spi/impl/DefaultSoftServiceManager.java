package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.config.SoftManagementProperties;
import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.service.SoftTargetCommandExecutor;
import com.chua.starter.soft.support.spi.SoftServiceManager;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import org.springframework.stereotype.Component;

@Component
public class DefaultSoftServiceManager implements SoftServiceManager {

    private final SoftManagementProperties properties;
    private final SoftTargetCommandExecutor localExecutor;
    private final SoftTargetCommandExecutor sshExecutor;
    private final SoftTargetCommandExecutor winRmExecutor;

    public DefaultSoftServiceManager(
            SoftManagementProperties properties,
            LocalSoftTargetCommandExecutor localExecutor,
            SshSoftTargetCommandExecutor sshExecutor,
            WinRmSoftTargetCommandExecutor winRmExecutor) {
        this.properties = properties;
        this.localExecutor = localExecutor;
        this.sshExecutor = sshExecutor;
        this.winRmExecutor = winRmExecutor;
    }

    @Override
    public SoftOperationResult register(SoftExecutionContext context) throws Exception {
        String script = renderedOrVersion(context, "SERVICE_REGISTER_SCRIPT", context.getVersion().getServiceRegisterScript());
        if (script == null || script.isBlank()) {
            script = SoftCommandSupport.isWindows(context.getTarget().getOsType())
                    ? createWinSwRegisterScript(context)
                    : createSystemdRegisterScript(context);
        }
        return executor(context).execute(context.getTarget(), SoftCommandSupport.renderScript(script, context, context.getInstallation().getInstallPath() + "/artifact.bin"));
    }

    @Override
    public SoftOperationResult unregister(SoftExecutionContext context) throws Exception {
        String script = renderedOrVersion(context, "SERVICE_UNREGISTER_SCRIPT", context.getVersion().getServiceUnregisterScript());
        if (script == null || script.isBlank()) {
            script = SoftCommandSupport.isWindows(context.getTarget().getOsType())
                    ? context.getInstallation().getInstallPath() + "\\winsw.exe uninstall"
                    : "systemctl disable --now " + context.getInstallation().getServiceName() + " && rm -f /etc/systemd/system/" + context.getInstallation().getServiceName() + ".service";
        }
        return executor(context).execute(context.getTarget(), SoftCommandSupport.renderScript(script, context, context.getInstallation().getInstallPath() + "/artifact.bin"));
    }

    @Override
    public SoftOperationResult start(SoftExecutionContext context) throws Exception {
        String script = blankToDefault(renderedOrVersion(context, "START_SCRIPT", context.getVersion().getStartScript()),
                SoftCommandSupport.isWindows(context.getTarget().getOsType())
                        ? context.getInstallation().getInstallPath() + "\\winsw.exe start"
                        : "systemctl start " + context.getInstallation().getServiceName());
        String rendered = SoftCommandSupport.renderScript(script, context, null);
        if (isLocalWindows(context)) {
            String stdoutPath = context.getInstallation().getInstallPath() + "\\logs\\service-start.out.log";
            String stderrPath = context.getInstallation().getInstallPath() + "\\logs\\service-start.err.log";
            return localExecutor.executeBackground(context.getTarget(), rendered, stdoutPath, stderrPath);
        }
        return executor(context).execute(context.getTarget(), rendered);
    }

    @Override
    public SoftOperationResult stop(SoftExecutionContext context) throws Exception {
        String script = blankToDefault(renderedOrVersion(context, "STOP_SCRIPT", context.getVersion().getStopScript()),
                SoftCommandSupport.isWindows(context.getTarget().getOsType())
                        ? context.getInstallation().getInstallPath() + "\\winsw.exe stop"
                        : "systemctl stop " + context.getInstallation().getServiceName());
        return executor(context).execute(context.getTarget(), SoftCommandSupport.renderScript(script, context, null));
    }

    @Override
    public SoftOperationResult restart(SoftExecutionContext context) throws Exception {
        String script = blankToDefault(renderedOrVersion(context, "RESTART_SCRIPT", context.getVersion().getRestartScript()),
                SoftCommandSupport.isWindows(context.getTarget().getOsType())
                        ? context.getInstallation().getInstallPath() + "\\winsw.exe restart"
                        : "systemctl restart " + context.getInstallation().getServiceName());
        return executor(context).execute(context.getTarget(), SoftCommandSupport.renderScript(script, context, null));
    }

    @Override
    public SoftOperationResult status(SoftExecutionContext context) throws Exception {
        String script = blankToDefault(renderedOrVersion(context, "STATUS_SCRIPT", context.getVersion().getStatusScript()),
                SoftCommandSupport.isWindows(context.getTarget().getOsType())
                        ? context.getInstallation().getInstallPath() + "\\winsw.exe status"
                        : "systemctl is-active " + context.getInstallation().getServiceName());
        return executor(context).execute(context.getTarget(), SoftCommandSupport.renderScript(script, context, null));
    }

    private SoftTargetCommandExecutor executor(SoftExecutionContext context) {
        String type = context.getTarget().getTargetType();
        if (localExecutor.supports(type)) {
            return localExecutor;
        }
        if (sshExecutor.supports(type)) {
            return sshExecutor;
        }
        return winRmExecutor;
    }

    private boolean isLocalWindows(SoftExecutionContext context) {
        return localExecutor.supports(context.getTarget().getTargetType())
                && SoftCommandSupport.isWindows(context.getTarget().getOsType());
    }

    private String createSystemdRegisterScript(SoftExecutionContext context) {
        String installPath = context.getInstallation().getInstallPath();
        String serviceName = context.getInstallation().getServiceName();
        String execStart = blankToDefault(context.getVersion().getStartScript(), installPath + "/start.sh");
        return "cat > /etc/systemd/system/" + serviceName + ".service <<'EOF'\n"
                + "[Unit]\nDescription=" + serviceName + "\nAfter=network.target\n\n"
                + "[Service]\nType=simple\nWorkingDirectory=" + installPath + "\nExecStart=" + execStart + "\nRestart=always\n\n"
                + "[Install]\nWantedBy=multi-user.target\nEOF\n"
                + "systemctl daemon-reload && systemctl enable --now " + serviceName;
    }

    private String createWinSwRegisterScript(SoftExecutionContext context) {
        String installPath = context.getInstallation().getInstallPath();
        String exePath = installPath + "\\winsw.exe";
        String xmlPath = installPath + "\\" + context.getInstallation().getServiceName() + ".xml";
        String execStart = blankToDefault(context.getVersion().getStartScript(), installPath + "\\start.bat");
        String xml = "<service><id>" + context.getInstallation().getServiceName() + "</id><name>" + context.getInstallation().getServiceName()
                + "</name><executable>" + execStart + "</executable><logpath>" + installPath + "\\logs</logpath></service>";
        return "Invoke-WebRequest -Uri " + SoftCommandSupport.powershellQuote(properties.getWinSwDownloadUrl()) + " -OutFile "
                + SoftCommandSupport.powershellQuote(exePath) + "; "
                + "[IO.File]::WriteAllText(" + SoftCommandSupport.powershellQuote(xmlPath) + ", " + SoftCommandSupport.powershellQuote(xml) + "); "
                + "& " + SoftCommandSupport.powershellQuote(exePath) + " install";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String renderedOrVersion(SoftExecutionContext context, String scriptCode, String versionScript) {
        if (context.getRenderedScripts() != null) {
            String rendered = context.getRenderedScripts().get(scriptCode);
            if (rendered != null && !rendered.isBlank()) {
                return rendered;
            }
        }
        return versionScript;
    }
}
