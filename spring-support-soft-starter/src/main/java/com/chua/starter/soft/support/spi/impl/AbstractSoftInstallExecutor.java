package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.entity.SoftInstallation;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.enums.SoftOperationStage;
import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.service.SoftTargetCommandExecutor;
import com.chua.starter.soft.support.spi.SoftCommandObserver;
import com.chua.starter.soft.support.spi.SoftInstallExecutor;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import com.chua.starter.soft.support.util.SoftJsons;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class AbstractSoftInstallExecutor implements SoftInstallExecutor {

    protected final SoftTargetCommandExecutor commandExecutor;

    protected AbstractSoftInstallExecutor(SoftTargetCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public SoftOperationResult install(SoftExecutionContext context) throws Exception {
        return install(context, null);
    }

    public SoftOperationResult install(SoftExecutionContext context, SoftCommandObserver observer) throws Exception {
        SoftInstallation installation = context.getInstallation();
        SoftPackageVersion version = context.getVersion();
        String artifactPath = buildArtifactPath(context);
        if (observer != null) {
            observer.onStage(SoftOperationStage.PREPARE, "准备安装环境", installation.getInstallPath());
        }
        prepareInstallDirectory(context);
        if (observer != null) {
            observer.onStage(SoftOperationStage.DOWNLOAD, "下载安装包", firstDownloadUrl(context));
        }
        downloadArtifact(context, artifactPath);
        String script = context.getRenderedScripts() == null ? null : context.getRenderedScripts().get("INSTALL_SCRIPT");
        if (script == null || script.isBlank()) {
            script = SoftCommandSupport.renderScript(version.getInstallScript(), context, artifactPath);
        }
        if (script == null || script.isBlank()) {
            script = defaultInstallCommand(context, artifactPath);
        }
        if (observer != null) {
            observer.onStage(SoftOperationStage.INSTALL, "执行安装脚本", script);
        }
        return commandExecutor.execute(context.getTarget(), script, observer);
    }

    @Override
    public SoftOperationResult uninstall(SoftExecutionContext context) throws Exception {
        return uninstall(context, null);
    }

    public SoftOperationResult uninstall(SoftExecutionContext context, SoftCommandObserver observer) throws Exception {
        if (observer != null) {
            observer.onStage(SoftOperationStage.PREPARE, "准备卸载环境", context.getInstallation().getInstallPath());
        }
        String script = context.getRenderedScripts() == null ? null : context.getRenderedScripts().get("UNINSTALL_SCRIPT");
        if (script == null || script.isBlank()) {
            script = SoftCommandSupport.renderScript(context.getVersion().getUninstallScript(), context, buildArtifactPath(context));
        }
        if (script == null || script.isBlank()) {
            script = defaultUninstallCommand(context);
        }
        if (observer != null) {
            observer.onStage(SoftOperationStage.UNINSTALL, "执行卸载脚本", script);
        }
        return commandExecutor.execute(context.getTarget(), script, observer);
    }

    protected String buildArtifactPath(SoftInstallation installation) {
        return installation.getInstallPath() + "/artifact.bin";
    }

    protected String buildArtifactPath(SoftExecutionContext context) {
        return context.getInstallation().getInstallPath() + "/" + resolveArtifactFileName(context);
    }

    protected void prepareInstallDirectory(SoftExecutionContext context) throws Exception {
        commandExecutor.execute(context.getTarget(), installDirectoryCommand(context.getInstallation().getInstallPath()));
    }

    protected String defaultInstallCommand(SoftExecutionContext context, String artifactPath) {
        String installPath = context.getInstallation().getInstallPath();
        String extension = artifactExtension(context);
        boolean windows = SoftCommandSupport.isWindows(context.getTarget().getOsType());
        if (".zip".equals(extension)) {
            return unzipCommand(artifactPath, installPath);
        }
        if (List.of(".tar.gz", ".tgz").contains(extension)) {
            return "tar -xzf " + quoted(artifactPath) + " -C " + quoted(installPath);
        }
        if (".tar".equals(extension)) {
            return "tar -xf " + quoted(artifactPath) + " -C " + quoted(installPath);
        }
        if (!windows && ".rpm".equals(extension)) {
            return "rpm -Uvh --force " + quoted(artifactPath)
                    + " || dnf install -y " + quoted(artifactPath)
                    + " || yum localinstall -y " + quoted(artifactPath);
        }
        if (!windows && ".deb".equals(extension)) {
            return "dpkg -i " + quoted(artifactPath) + " || apt-get install -f -y";
        }
        if (windows && ".msi".equals(extension)) {
            return "Start-Process msiexec.exe -Wait -ArgumentList @('/i', "
                    + quoted(artifactPath)
                    + ", '/qn', '/norestart')";
        }
        if (windows && ".exe".equals(extension)) {
            return standaloneExecutable(context)
                    ? "Write-Output ('standalone executable ready: ' + " + quoted(artifactPath) + ")"
                    : "Start-Process -Wait -FilePath " + quoted(artifactPath) + " -ArgumentList @('/S', '/quiet', '/norestart')";
        }
        return "echo installed > " + quoted(installPath + "/.installed");
    }

    protected String defaultUninstallCommand(SoftExecutionContext context) {
        return deleteDirectoryCommand(context.getInstallation().getInstallPath());
    }

    protected String firstDownloadUrl(SoftExecutionContext context) {
        List<String> urls = context.getVersion().getDownloadUrls();
        if (urls == null || urls.isEmpty()) {
            urls = SoftJsons.toStringList(context.getVersion().getDownloadUrlsJson());
        }
        if (urls.isEmpty()) {
            throw new IllegalStateException("未配置下载地址");
        }
        return urls.getFirst();
    }

    protected abstract void downloadArtifact(SoftExecutionContext context, String artifactPath) throws Exception;

    protected abstract String installDirectoryCommand(String installPath);

    protected abstract String deleteDirectoryCommand(String installPath);

    protected abstract String unzipCommand(String artifactPath, String installPath);

    protected abstract String quoted(String value);

    private String artifactExtension(SoftExecutionContext context) {
        String fileName = resolveArtifactFileName(context).toLowerCase(Locale.ROOT);
        for (String extension : List.of(".tar.gz", ".tgz", ".tar", ".zip", ".7z", ".rpm", ".deb", ".msi", ".exe")) {
            if (fileName.endsWith(extension)) {
                return extension;
            }
        }
        return "";
    }

    private boolean standaloneExecutable(SoftExecutionContext context) {
        Map<String, Object> metadata = SoftJsons.toMap(context.getVersion().getMetadataJson());
        Object value = metadata.get("standaloneExecutable");
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String resolveArtifactFileName(SoftExecutionContext context) {
        Map<String, Object> metadata = SoftJsons.toMap(context.getVersion().getMetadataJson());
        Object metadataFileName = metadata.get("artifactFileName");
        if (metadataFileName != null && !String.valueOf(metadataFileName).isBlank()) {
            return String.valueOf(metadataFileName);
        }
        try {
            URI uri = URI.create(firstDownloadUrl(context));
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                if (!fileName.isBlank()) {
                    return fileName;
                }
            }
        } catch (Exception ignored) {
        }
        return "artifact.bin";
    }
}
