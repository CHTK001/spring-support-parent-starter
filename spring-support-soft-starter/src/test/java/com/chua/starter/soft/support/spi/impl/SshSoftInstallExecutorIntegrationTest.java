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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SshSoftInstallExecutorIntegrationTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Test
    void shouldInstallAndUninstallOnRealSshHost() throws Exception {
        String host = readRequired("soft.it.ssh.host", "SOFT_IT_SSH_HOST");
        String username = readRequired("soft.it.ssh.username", "SOFT_IT_SSH_USERNAME");
        String password = readRequired("soft.it.ssh.password", "SOFT_IT_SSH_PASSWORD");
        int port = Integer.parseInt(readOptional("soft.it.ssh.port", "SOFT_IT_SSH_PORT", "22"));
        String baseDirectory = readOptional("soft.it.ssh.baseDir", "SOFT_IT_SSH_BASE_DIR", "/tmp/codex-soft-it");
        Assumptions.assumeTrue(host != null && username != null && password != null, "未提供 SSH 集成测试参数");

        SoftTarget target = new SoftTarget();
        target.setTargetType("SSH");
        target.setOsType("LINUX");
        target.setHost(host);
        target.setPort(port);
        target.setUsername(username);
        target.setPassword(password);
        target.setBaseDirectory(baseDirectory);

        String installPath = baseDirectory + "/soft-install-" + FORMATTER.format(LocalDateTime.now());
        SoftInstallation installation = new SoftInstallation();
        installation.setInstallationName("codex-soft-it");
        installation.setInstallPath(installPath);
        installation.setServiceName("codex-soft-it");

        SoftPackage softPackage = new SoftPackage();
        softPackage.setPackageCode("generic");
        softPackage.setPackageName("Generic Test Package");

        SoftPackageVersion version = new SoftPackageVersion();
        version.setVersionCode("it-1");
        version.setVersionName("it-1");
        version.setDownloadUrls(List.of("test://integration-artifact"));
        version.setInstallScript("""
                set -e
                test -f ${artifactPath}
                mkdir -p ${installPath}/conf ${installPath}/logs
                cp ${artifactPath} ${installPath}/artifact-copy.txt
                printf 'installed-from-%s\\n' "${versionCode}" > ${installPath}/.installed
                printf 'install-line-1\\n'
                printf 'install-line-2\\n'
                """);
        version.setUninstallScript("""
                set -e
                test -d ${installPath}
                rm -rf ${installPath}
                printf 'uninstall-done\\n'
                """);

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

        SshSoftTargetCommandExecutor commandExecutor = new SshSoftTargetCommandExecutor();
        TestSshSoftInstallExecutor executor = new TestSshSoftInstallExecutor(commandExecutor);
        commandExecutor.execute(target, "rm -rf '" + installPath + "'");

        List<String> installLines = new ArrayList<>();
        SoftOperationResult installResult = executor.install(context, new CollectingObserver(installLines));

        assertTrue(installResult.isSuccess(), installResult.getOutput());
        assertTrue(installResult.getOutput().contains("install-line-1"));
        assertEquals("installed-from-it-1\n", commandExecutor.readFile(target, installPath + "/.installed"));
        assertTrue(commandExecutor.execute(target, "test -f '" + installPath + "/artifact-copy.txt'").isSuccess());
        assertTrue(installLines.stream().anyMatch(line -> line.contains("install-line-2")));

        List<String> uninstallLines = new ArrayList<>();
        SoftOperationResult uninstallResult = executor.uninstall(context, new CollectingObserver(uninstallLines));

        assertTrue(uninstallResult.isSuccess(), uninstallResult.getOutput());
        assertTrue(uninstallLines.stream().anyMatch(line -> line.contains("uninstall-done")));
        assertTrue(commandExecutor.execute(target, "test ! -e '" + installPath + "'").isSuccess());
    }

    private String readRequired(String propertyKey, String envKey) {
        return readOptional(propertyKey, envKey, null);
    }

    private String readOptional(String propertyKey, String envKey, String defaultValue) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    private static final class TestSshSoftInstallExecutor extends AbstractSoftInstallExecutor {

        private TestSshSoftInstallExecutor(SshSoftTargetCommandExecutor commandExecutor) {
            super(commandExecutor);
        }

        @Override
        public boolean supports(String targetType) {
            return "SSH".equalsIgnoreCase(targetType);
        }

        @Override
        protected void downloadArtifact(SoftExecutionContext context, String artifactPath) throws Exception {
            commandExecutor.execute(context.getTarget(), "printf 'integration-artifact\\n' > " + quoted(artifactPath));
        }

        @Override
        protected String installDirectoryCommand(String installPath) {
            return "mkdir -p " + quoted(installPath);
        }

        @Override
        protected String deleteDirectoryCommand(String installPath) {
            return "rm -rf " + quoted(installPath);
        }

        @Override
        protected String unzipCommand(String artifactPath, String installPath) {
            return "unzip -o " + quoted(artifactPath) + " -d " + quoted(installPath);
        }

        @Override
        protected String quoted(String value) {
            return "'" + value.replace("'", "'\"'\"'") + "'";
        }
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
