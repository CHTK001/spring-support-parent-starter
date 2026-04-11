package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import org.springframework.stereotype.Component;

@Component
public class WinRmSoftInstallExecutor extends AbstractSoftInstallExecutor {

    public WinRmSoftInstallExecutor(WinRmSoftTargetCommandExecutor commandExecutor) {
        super(commandExecutor);
    }

    @Override
    public boolean supports(String targetType) {
        return "WINRM".equalsIgnoreCase(targetType);
    }

    @Override
    protected void downloadArtifact(SoftExecutionContext context, String artifactPath) throws Exception {
        String command = "Invoke-WebRequest -Uri "
                + SoftCommandSupport.powershellQuote(firstDownloadUrl(context))
                + " -OutFile "
                + SoftCommandSupport.powershellQuote(artifactPath);
        var result = commandExecutor.execute(context.getTarget(), command);
        if (!result.isSuccess()) {
            throw new IllegalStateException("下载安装包失败: " + result.getOutput());
        }
    }

    @Override
    protected String installDirectoryCommand(String installPath) {
        return "New-Item -ItemType Directory -Force -Path " + SoftCommandSupport.powershellQuote(installPath) + " | Out-Null";
    }

    @Override
    protected String deleteDirectoryCommand(String installPath) {
        return "Remove-Item -Recurse -Force -LiteralPath " + SoftCommandSupport.powershellQuote(installPath);
    }

    @Override
    protected String unzipCommand(String artifactPath, String installPath) {
        return "Expand-Archive -Force -Path " + SoftCommandSupport.powershellQuote(artifactPath) + " -DestinationPath " + SoftCommandSupport.powershellQuote(installPath);
    }

    @Override
    protected String quoted(String value) {
        return SoftCommandSupport.powershellQuote(value);
    }
}
