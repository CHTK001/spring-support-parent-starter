package com.chua.starter.soft.support.util;

import com.chua.starter.soft.support.entity.SoftInstallation;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.spi.SoftCommandObserver;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SoftCommandSupport {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private SoftCommandSupport() {
    }

    public static boolean isWindows(String osType) {
        return osType != null && osType.toLowerCase().contains("win");
    }

    public static String bashQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static String powershellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    public static SoftOperationResult runLocal(List<String> command) throws Exception {
        return runLocal(command, null);
    }

    public static SoftOperationResult runLocal(List<String> command, SoftCommandObserver observer) throws Exception {
        return runLocal(command, observer, true);
    }

    public static SoftOperationResult runLocal(
            List<String> command,
            SoftCommandObserver observer,
            boolean mergeErrorStream
    ) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(mergeErrorStream);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        if (mergeErrorStream) {
            Thread stdoutThread = startStreamCollector(process.getInputStream(), output, observer == null ? null : observer::onStdout);
            stdoutThread.join();
        } else {
            Thread stdoutThread = startStreamCollector(process.getInputStream(), output, observer == null ? null : observer::onStdout);
            Thread stderrThread = startStreamCollector(process.getErrorStream(), output, observer == null ? null : observer::onStderr);
            stdoutThread.join();
            stderrThread.join();
        }
        int exitCode = process.waitFor();
        return SoftOperationResult.builder()
                .success(exitCode == 0)
                .accepted(exitCode == 0)
                .finished(true)
                .exitCode(exitCode)
                .command(String.join(" ", command))
                .message(exitCode == 0 ? "执行成功" : "执行失败")
                .output(output.toString())
                .build();
    }

    public static SoftOperationResult runLocalBackground(
            List<String> command,
            String stdoutPath,
            String stderrPath
    ) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(stdoutPath == null || stdoutPath.isBlank()
                ? ProcessBuilder.Redirect.DISCARD
                : ProcessBuilder.Redirect.to(prepareRedirectFile(stdoutPath)));
        builder.redirectError(stderrPath == null || stderrPath.isBlank()
                ? ProcessBuilder.Redirect.DISCARD
                : ProcessBuilder.Redirect.to(prepareRedirectFile(stderrPath)));
        Process process = builder.start();
        boolean finished = process.waitFor(1500, TimeUnit.MILLISECONDS);
        Integer exitCode = finished ? process.exitValue() : null;
        String output = finished ? joinOutputs(readFileIfExists(stdoutPath), readFileIfExists(stderrPath)) : null;
        boolean success = !finished || exitCode == 0;
        return SoftOperationResult.builder()
                .success(success)
                .accepted(success)
                .finished(finished)
                .exitCode(exitCode)
                .processId(process.pid())
                .command(String.join(" ", command))
                .message(finished
                        ? (exitCode == 0 ? "后台命令已完成" : "后台命令执行失败")
                        : "后台进程已受理")
                .output(output)
                .stdoutPath(stdoutPath)
                .stderrPath(stderrPath)
                .build();
    }

    public static List<String> wrapShellCommand(String osType, String command) {
        List<String> args = new ArrayList<>();
        if (isWindows(osType)) {
            args.add("powershell");
            args.add("-NoProfile");
            args.add("-Command");
            args.add(wrapPowerShellCommand(command));
            return args;
        }
        args.add("sh");
        args.add("-lc");
        args.add(command);
        return args;
    }

    private static String wrapPowerShellCommand(String command) {
        String script = command == null ? "" : command;
        return "$ErrorActionPreference = 'Stop'; "
                + "$ProgressPreference = 'SilentlyContinue'; "
                + "$global:LASTEXITCODE = 0; "
                + "try { "
                + script
                + "; if ($global:LASTEXITCODE -ne 0) { exit $global:LASTEXITCODE }; exit 0 "
                + "} catch { "
                + "Write-Error ($_ | Out-String); exit 1 "
                + "}";
    }

    public static String renderScript(String script, SoftExecutionContext context, String artifactPath) {
        if (script == null || script.isBlank()) {
            return script;
        }
        SoftInstallation installation = context.getInstallation();
        SoftPackageVersion version = context.getVersion();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("installPath", installation == null ? null : installation.getInstallPath());
        variables.put("serviceName", installation == null ? null : installation.getServiceName());
        variables.put("artifactPath", artifactPath);
        variables.put("versionCode", version == null ? null : version.getVersionCode());
        variables.put("versionName", version == null ? null : version.getVersionName());
        if (context.getResolvedVariables() != null) {
            variables.putAll(context.getResolvedVariables());
        }
        return renderTemplate(normalizeLegacyArtifactPlaceholder(script), variables);
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }

    public static String renderTemplate(String template, Map<String, ?> variables) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables == null ? null : variables.get(key);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String normalizeLegacyArtifactPlaceholder(String script) {
        return script
                .replace("${installPath}/artifact.bin", "${artifactPath}")
                .replace("${installPath}\\artifact.bin", "${artifactPath}")
                .replace("${installPath}/artifact.exe", "${artifactPath}")
                .replace("${installPath}\\artifact.exe", "${artifactPath}");
    }

    private static Thread startStreamCollector(
            java.io.InputStream stream,
            StringBuilder output,
            Consumer<String> consumer
    ) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        output.append(line).append(System.lineSeparator());
                    }
                    if (consumer != null) {
                        consumer.accept(line);
                    }
                }
            } catch (Exception ignored) {
            }
        }, "soft-local-command-stream");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static File prepareRedirectFile(String path) {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return file;
    }

    private static String readFileIfExists(String path) throws Exception {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        return java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private static String joinOutputs(String stdout, String stderr) {
        String left = stdout == null ? "" : stdout.strip();
        String right = stderr == null ? "" : stderr.strip();
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank()) {
            return left;
        }
        return left + System.lineSeparator() + right;
    }
}
