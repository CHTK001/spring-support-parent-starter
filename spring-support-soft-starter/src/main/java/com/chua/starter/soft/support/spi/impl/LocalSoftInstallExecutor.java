package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.service.SoftTargetCommandExecutor;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class LocalSoftInstallExecutor extends AbstractSoftInstallExecutor {

    public LocalSoftInstallExecutor(LocalSoftTargetCommandExecutor commandExecutor) {
        super(commandExecutor);
    }

    @Override
    public boolean supports(String targetType) {
        return "LOCAL".equalsIgnoreCase(targetType);
    }

    @Override
    protected void downloadArtifact(SoftExecutionContext context, String artifactPath) throws Exception {
        Path path = Path.of(artifactPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (var input = java.net.URI.create(firstDownloadUrl(context)).toURL().openStream()) {
            Files.copy(input, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    protected String installDirectoryCommand(String installPath) {
        return SoftCommandSupport.isWindows(System.getProperty("os.name"))
                ? "New-Item -ItemType Directory -Force -Path " + SoftCommandSupport.powershellQuote(installPath) + " | Out-Null"
                : "mkdir -p " + SoftCommandSupport.bashQuote(installPath);
    }

    @Override
    protected String deleteDirectoryCommand(String installPath) {
        return SoftCommandSupport.isWindows(System.getProperty("os.name"))
                ? "Remove-Item -Recurse -Force -LiteralPath " + SoftCommandSupport.powershellQuote(installPath)
                : "rm -rf " + SoftCommandSupport.bashQuote(installPath);
    }

    @Override
    protected String unzipCommand(String artifactPath, String installPath) {
        return SoftCommandSupport.isWindows(System.getProperty("os.name"))
                ? "Expand-Archive -Force -Path " + SoftCommandSupport.powershellQuote(artifactPath) + " -DestinationPath " + SoftCommandSupport.powershellQuote(installPath)
                : "unzip -o " + SoftCommandSupport.bashQuote(artifactPath) + " -d " + SoftCommandSupport.bashQuote(installPath);
    }

    @Override
    protected String quoted(String value) {
        return SoftCommandSupport.isWindows(System.getProperty("os.name"))
                ? SoftCommandSupport.powershellQuote(value)
                : SoftCommandSupport.bashQuote(value);
    }
}
