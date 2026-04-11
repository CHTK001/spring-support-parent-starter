package com.chua.starter.soft.support.spi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.soft.support.entity.SoftInstallation;
import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.spi.SoftCommandObserver;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSoftInstallExecutorIntegrationTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @TempDir
    Path tempDir;

    @Test
    void shouldInstallAndUninstallOnLocalHost() throws Exception {
        boolean windows = SoftCommandSupport.isWindows(System.getProperty("os.name"));
        Path artifactSource = tempDir.resolve("artifact-source.txt");
        Files.writeString(artifactSource, "local-artifact\n", StandardCharsets.UTF_8);

        SoftTarget target = new SoftTarget();
        target.setTargetType("LOCAL");
        target.setOsType(System.getProperty("os.name"));
        target.setBaseDirectory(tempDir.toString());

        String installPath = tempDir.resolve("soft-install-" + FORMATTER.format(LocalDateTime.now())).toString();
        SoftInstallation installation = new SoftInstallation();
        installation.setInstallationName("codex-soft-local-it");
        installation.setInstallPath(installPath);
        installation.setServiceName("codex-soft-local-it");

        SoftPackage softPackage = new SoftPackage();
        softPackage.setPackageCode("generic");
        softPackage.setPackageName("Generic Local Test Package");

        SoftPackageVersion version = new SoftPackageVersion();
        version.setVersionCode("local-it-1");
        version.setVersionName("local-it-1");
        version.setDownloadUrls(List.of(artifactSource.toUri().toString()));
        version.setInstallScript(windows ? windowsInstallScript() : unixInstallScript());
        version.setUninstallScript(windows ? windowsUninstallScript() : unixUninstallScript());

        SoftExecutionContext context = SoftExecutionContext.builder()
                .softPackage(softPackage)
                .version(version)
                .target(target)
                .installation(installation)
                .installOptions(Map.of())
                .serviceOptions(Map.of())
                .configOptions(Map.of())
                .resolvedVariables(Map.of(
                        "installPath", installPath,
                        "serviceName", installation.getServiceName(),
                        "versionCode", version.getVersionCode()))
                .build();

        LocalSoftTargetCommandExecutor commandExecutor = new LocalSoftTargetCommandExecutor();
        LocalSoftInstallExecutor executor = new LocalSoftInstallExecutor(commandExecutor);

        List<String> installLines = new ArrayList<>();
        SoftOperationResult installResult = executor.install(context, new CollectingObserver(installLines));

        assertTrue(installResult.isSuccess(), installResult.getOutput());
        assertTrue(installResult.getOutput().contains("install-line-1"));
        assertTrue(installLines.stream().anyMatch(line -> line.contains("install-line-2")));
        assertEquals(
                "installed-from-local-it-1",
                Files.readString(Path.of(installPath, ".installed"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(Path.of(installPath, "artifact-copy.txt")));

        List<String> uninstallLines = new ArrayList<>();
        SoftOperationResult uninstallResult = executor.uninstall(context, new CollectingObserver(uninstallLines));

        assertTrue(uninstallResult.isSuccess(), uninstallResult.getOutput());
        assertTrue(uninstallLines.stream().anyMatch(line -> line.contains("uninstall-done")));
        assertTrue(Files.notExists(Path.of(installPath)));
    }

    private String windowsInstallScript() {
        return """
                if (!(Test-Path -LiteralPath '${artifactPath}')) { throw 'artifact-missing' }
                New-Item -ItemType Directory -Force -Path (Join-Path '${installPath}' 'conf') | Out-Null
                New-Item -ItemType Directory -Force -Path (Join-Path '${installPath}' 'logs') | Out-Null
                Copy-Item -LiteralPath '${artifactPath}' -Destination (Join-Path '${installPath}' 'artifact-copy.txt') -Force
                [System.IO.File]::WriteAllText((Join-Path '${installPath}' '.installed'), 'installed-from-${versionCode}')
                Write-Output 'install-line-1'
                Write-Output 'install-line-2'
                """;
    }

    private String unixInstallScript() {
        return """
                set -e
                test -f '${artifactPath}'
                mkdir -p '${installPath}/conf' '${installPath}/logs'
                cp '${artifactPath}' '${installPath}/artifact-copy.txt'
                printf 'installed-from-%s' '${versionCode}' > '${installPath}/.installed'
                printf 'install-line-1\\n'
                printf 'install-line-2\\n'
                """;
    }

    private String windowsUninstallScript() {
        return """
                if (!(Test-Path -LiteralPath '${installPath}')) { throw 'install-missing' }
                Remove-Item -Recurse -Force -LiteralPath '${installPath}'
                Write-Output 'uninstall-done'
                """;
    }

    private String unixUninstallScript() {
        return """
                set -e
                test -d '${installPath}'
                rm -rf '${installPath}'
                printf 'uninstall-done\\n'
                """;
    }

    private record CollectingObserver(List<String> lines) implements SoftCommandObserver {
        @Override
        public void onStdout(String line) {
            lines.add(line);
        }

        @Override
        public void onStderr(String line) {
            lines.add(line);
        }
    }
}
