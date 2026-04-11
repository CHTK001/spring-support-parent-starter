package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import org.springframework.stereotype.Component;

@Component
public class SshSoftInstallExecutor extends AbstractSoftInstallExecutor {

    public SshSoftInstallExecutor(SshSoftTargetCommandExecutor commandExecutor) {
        super(commandExecutor);
    }

    @Override
    public boolean supports(String targetType) {
        return "SSH".equalsIgnoreCase(targetType);
    }

    @Override
    protected void downloadArtifact(SoftExecutionContext context, String artifactPath) throws Exception {
        String command = "curl -L "
                + SoftCommandSupport.bashQuote(firstDownloadUrl(context))
                + " -o "
                + SoftCommandSupport.bashQuote(artifactPath)
                + " || wget -O "
                + SoftCommandSupport.bashQuote(artifactPath)
                + " "
                + SoftCommandSupport.bashQuote(firstDownloadUrl(context));
        var result = commandExecutor.execute(context.getTarget(), command);
        if (!result.isSuccess()) {
            throw new IllegalStateException("下载安装包失败: " + result.getOutput());
        }
    }

    @Override
    protected String installDirectoryCommand(String installPath) {
        return "mkdir -p " + SoftCommandSupport.bashQuote(installPath);
    }

    @Override
    protected String deleteDirectoryCommand(String installPath) {
        return "rm -rf " + SoftCommandSupport.bashQuote(installPath);
    }

    @Override
    protected String unzipCommand(String artifactPath, String installPath) {
        return "unzip -o " + SoftCommandSupport.bashQuote(artifactPath) + " -d " + SoftCommandSupport.bashQuote(installPath);
    }

    @Override
    protected String quoted(String value) {
        return SoftCommandSupport.bashQuote(value);
    }
}
