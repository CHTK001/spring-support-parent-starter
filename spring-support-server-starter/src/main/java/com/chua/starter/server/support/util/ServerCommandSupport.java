package com.chua.starter.server.support.util;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerCommandSupport {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private ServerCommandSupport() {
    }

    public static boolean isWindows(String osType) {
        return osType != null && osType.toLowerCase().contains("win");
    }

    public static String powershellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    public static List<String> wrapShellCommand(String osType, String command) {
        List<String> args = new ArrayList<>();
        if (isWindows(osType)) {
            args.add("powershell");
            args.add("-NoProfile");
            args.add("-EncodedCommand");
            args.add(encodePowerShellCommand(wrapPowerShellCommand(command)));
            return args;
        }
        args.add("sh");
        args.add("-lc");
        args.add(command);
        return args;
    }

    public static CommandResult runLocal(ServerHost host, String command) throws Exception {
        String osType = host == null || "LOCAL".equalsIgnoreCase(host.getServerType())
                ? System.getProperty("os.name")
                : host.getOsType();
        ProcessBuilder builder = new ProcessBuilder(wrapShellCommand(osType, command));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        }
        int exitCode = process.waitFor();
        return new CommandResult(exitCode == 0, exitCode, output.toString());
    }

    public static String renderScript(String script, ServerHost host, ServerService service) {
        if (script == null || script.isBlank()) {
            return script;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("serviceName", service == null ? null : service.getServiceName());
        variables.put("serviceCode", service == null ? null : service.getServiceCode());
        variables.put("installPath", service == null ? null : service.getInstallPath());
        variables.put("serverName", host == null ? null : host.getServerName());
        variables.put("host", host == null ? null : host.getHost());
        variables.put("baseDirectory", host == null ? null : host.getBaseDirectory());
        return renderTemplate(script, variables);
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

    /**
     * PowerShell 的 -EncodedCommand 需要 UTF-16LE Base64，避免多行脚本和特殊字符在参数层被截断。
     */
    private static String encodePowerShellCommand(String command) {
        byte[] bytes = (command == null ? "" : command).getBytes(StandardCharsets.UTF_16LE);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public record CommandResult(boolean success, int exitCode, String output) {
    }
}
