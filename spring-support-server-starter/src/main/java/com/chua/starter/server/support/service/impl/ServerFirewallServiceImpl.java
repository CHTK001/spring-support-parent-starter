package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.FirewallRule;
import com.chua.starter.server.support.model.FirewallStatus;
import com.chua.starter.server.support.service.ServerFirewallService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.util.ServerCommandSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerFirewallServiceImpl implements ServerFirewallService {

    private final ServerHostService serverHostService;
    private final ServerHostCommandExecutor serverHostCommandExecutor;

    @Override
    public FirewallStatus getStatus(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            String output = execute(host, "netsh advfirewall show allprofiles");
            return parseWindowsStatus(output);
        } else {
            String output = execute(host, "iptables -L -n --line-numbers 2>/dev/null; echo '---'; ufw status 2>/dev/null || echo 'ufw not available'");
            return parseLinuxStatus(output);
        }
    }

    @Override
    public List<FirewallRule> listRules(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            String output = execute(host, "netsh advfirewall firewall show rule name=all verbose");
            return parseWindowsRules(output);
        } else {
            String output = execute(host, "iptables -L INPUT -n --line-numbers -v 2>/dev/null");
            return parseLinuxRules(output);
        }
    }

    @Override
    public void addRule(Integer serverId, FirewallRule rule) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            String dir = "in".equalsIgnoreCase(rule.getDirection()) ? "in" : "out";
            String action = "allow".equalsIgnoreCase(rule.getAction()) ? "allow" : "block";
            String proto = rule.getProtocol() != null && !"any".equalsIgnoreCase(rule.getProtocol())
                    ? " protocol=" + rule.getProtocol() : "";
            String port = StringUtils.hasText(rule.getPort()) ? " localport=" + rule.getPort() : "";
            String name = StringUtils.hasText(rule.getName()) ? " name=" + powershellQuote(rule.getName()) : "";
            String cmd = String.format("netsh advfirewall firewall add rule%s dir=%s action=%s%s%s",
                    name, dir, action, proto, port);
            execute(host, cmd);
        } else {
            String action = "allow".equalsIgnoreCase(rule.getAction()) ? "ACCEPT" : "DROP";
            String proto = rule.getProtocol() != null && !"any".equalsIgnoreCase(rule.getProtocol())
                    ? " -p " + rule.getProtocol() : "";
            String port = StringUtils.hasText(rule.getPort()) ? " --dport " + rule.getPort() : "";
            String source = StringUtils.hasText(rule.getSourceIp()) ? " -s " + rule.getSourceIp() : "";
            String chain = "in".equalsIgnoreCase(rule.getDirection()) ? "INPUT" : "OUTPUT";
            String cmd = String.format("iptables -A %s%s%s -j %s%s", chain, proto, port, action, source);
            execute(host, cmd);
        }
    }

    @Override
    public void updateRule(Integer serverId, Integer ruleIndex, FirewallRule rule) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            // Windows: delete old rule and add new one
            String oldName = StringUtils.hasText(rule.getName())
                    ? " name=" + powershellQuote(rule.getName()) : "";
            if (StringUtils.hasText(oldName)) {
                execute(host, "netsh advfirewall firewall delete rule " + oldName);
            }
            addRule(serverId, rule);
        } else {
            // Linux: replace rule by index
            String action = "allow".equalsIgnoreCase(rule.getAction()) ? "ACCEPT" : "DROP";
            String proto = rule.getProtocol() != null && !"any".equalsIgnoreCase(rule.getProtocol())
                    ? " -p " + rule.getProtocol() : "";
            String port = StringUtils.hasText(rule.getPort()) ? " --dport " + rule.getPort() : "";
            String source = StringUtils.hasText(rule.getSourceIp()) ? " -s " + rule.getSourceIp() : "";
            String chain = "in".equalsIgnoreCase(rule.getDirection()) ? "INPUT" : "OUTPUT";
            // Delete and insert
            execute(host, String.format("iptables -D %s %d", chain, ruleIndex));
            execute(host, String.format("iptables -I %s %d%s%s -j %s%s", chain, ruleIndex, proto, port, action, source));
        }
    }

    @Override
    public void deleteRule(Integer serverId, Integer ruleIndex) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            // Windows doesn't use ruleIndex - rely on name from frontend
            execute(host, String.format("netsh advfirewall firewall delete rule name=%s",
                    powershellQuote("rule-" + ruleIndex)));
        } else {
            execute(host, String.format("iptables -D INPUT %d", ruleIndex));
        }
    }

    @Override
    public void toggleRule(Integer serverId, Integer ruleIndex, boolean enabled) throws Exception {
        ServerHost host = requireHost(serverId);
        if (enabled) {
            execute(host, String.format("iptables -I INPUT %d -j ACCEPT", ruleIndex));
        } else {
            execute(host, String.format("iptables -D INPUT %d", ruleIndex));
        }
    }

    private FirewallStatus parseWindowsStatus(String output) {
        boolean enabled = output != null && output.toLowerCase().contains("state on");
        return FirewallStatus.builder()
                .enabled(enabled)
                .activeRules(countMatches(output, "Rule "))
                .firewallType("Windows Firewall")
                .defaultInputPolicy(extractPolicy(output, "Inbound"))
                .defaultOutputPolicy(extractPolicy(output, "Outbound"))
                .build();
    }

    private FirewallStatus parseLinuxStatus(String output) {
        if (!StringUtils.hasText(output)) {
            return FirewallStatus.builder().enabled(false).activeRules(0).firewallType("unknown").build();
        }
        boolean enabled = output.contains("Chain") || output.contains("Status: active");
        int rules = countMatches(output, "^Chain|^[0-9]+\\s+");
        return FirewallStatus.builder()
                .enabled(enabled)
                .activeRules(rules)
                .firewallType("iptables")
                .defaultInputPolicy(extractIptablesPolicy(output, "INPUT"))
                .defaultOutputPolicy(extractIptablesPolicy(output, "OUTPUT"))
                .defaultForwardPolicy(extractIptablesPolicy(output, "FORWARD"))
                .build();
    }

    private List<FirewallRule> parseWindowsRules(String output) {
        List<FirewallRule> rules = new ArrayList<>();
        if (!StringUtils.hasText(output)) return rules;
        String[] blocks = output.split("Rule Name:\\s*");
        int idx = 0;
        for (String block : blocks) {
            if (!StringUtils.hasText(block.trim())) continue;
            idx++;
            String name = extractLine(block, 0);
            String dir = block.toLowerCase().contains("dir:in") ? "in" : "out";
            String action = block.toLowerCase().contains("action:allow") ? "allow" : "deny";
            String proto = extractValue(block, "Protocol:");
            String port = extractValue(block, "LocalPort:");
            rules.add(FirewallRule.builder()
                    .ruleIndex(idx).name(name).direction(dir).action(action)
                    .protocol(proto.toLowerCase()).port(port).enabled(true).build());
        }
        return rules;
    }

    private List<FirewallRule> parseLinuxRules(String output) {
        List<FirewallRule> rules = new ArrayList<>();
        if (!StringUtils.hasText(output)) return rules;
        String[] lines = output.split("\n");
        Pattern p = Pattern.compile("^\\s*(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+.*");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                try {
                    int idx = Integer.parseInt(m.group(1));
                    String target = m.group(2);
                    String proto = m.group(3);
                    String rest = line.substring(m.end()).trim();
                    rules.add(FirewallRule.builder()
                            .ruleIndex(idx).name("Rule " + idx)
                            .direction("in").action("allow".equalsIgnoreCase(target) ? "allow" : "deny")
                            .protocol(proto).enabled(true).description(rest).build());
                } catch (Exception ignored) {
                }
            }
        }
        return rules;
    }

    private String extractPolicy(String output, String direction) {
        if (output == null) return "";
        Pattern p = Pattern.compile(direction + ".*?policy[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(output);
        return m.find() ? m.group(1).toUpperCase() : "";
    }

    private String extractIptablesPolicy(String output, String chain) {
        Pattern p = Pattern.compile("Chain " + chain + ".*?policy\\s+(\\w+)");
        Matcher m = p.matcher(output);
        return m.find() ? m.group(1).toUpperCase() : "";
    }

    private String extractLine(String text, int lineIdx) {
        String[] lines = text.split("\n");
        return lineIdx < lines.length ? lines[lineIdx].trim() : "";
    }

    private String extractValue(String text, String key) {
        Pattern p = Pattern.compile(key + "\\s+(\\S+)");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private int countMatches(String text, String regex) {
        if (text == null) return 0;
        int count = 0;
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        while (m.find()) count++;
        return count;
    }

    private String powershellQuote(String value) {
        return ServerCommandSupport.powershellQuote(value);
    }

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) throw new IllegalArgumentException("未找到服务器");
        return host;
    }

    private boolean isWindows(ServerHost host) {
        String osType = host == null || !StringUtils.hasText(host.getOsType())
                ? System.getProperty("os.name") : host.getOsType();
        return ServerCommandSupport.isWindows(osType);
    }

    private String execute(ServerHost host, String command) throws Exception {
        ServerCommandSupport.CommandResult result = serverHostCommandExecutor.execute(host, command);
        return result != null ? result.output() : "";
    }
}
