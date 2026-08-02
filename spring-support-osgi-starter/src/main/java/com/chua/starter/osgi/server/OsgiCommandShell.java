package com.chua.starter.osgi.server;

import com.chua.common.support.osgi.OsgiLauncher;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.shell.ShellFactory;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * SSH 命令执行 shell 工厂。
 * <p>
 * 非交互式 shell，从 stdin 读取一行命令，通过 OSGi 框架执行并返回结果。
 * 适用于通过 SSH 单次执行命令（如 install/start/lb/stop 等）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OsgiCommandShell implements ShellFactory {

    private final OsgiLauncher launcher;

    public OsgiCommandShell(OsgiLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public org.apache.sshd.server.shell.ShellFactory.Shell createShell(ChannelSession channel) {
        return new org.apache.sshd.server.shell.ShellFactory.Shell() {
            @Override
            public void start(InputStream stdin, OutputStream stdout, OutputStream stderr) {
                try {
                    byte[] buf = new byte[8192];
                    int len = stdin.read(buf);
                    if (len <= 0) {
                        return;
                    }
                    String input = new String(buf, 0, len).trim();
                    if (input.isEmpty()) {
                        return;
                    }
                    String result = executeCommand(input);
                    stdout.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    stdout.flush();
                } catch (Exception e) {
                    try {
                        stderr.write(("ERROR: " + e.getMessage() + "\n").getBytes());
                        stderr.flush();
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void destroy(ChannelSession channel) {
            }
        };
    }

    private String executeCommand(String input) {
        if (launcher == null || launcher.getBundleContext() == null) {
            return "ERROR: OSGi framework not available\n";
        }
        try {
            String cmd = input.trim();
            String[] parts = cmd.split("\\s+");
            if (parts.length == 0) {
                return "";
            }
            String command = parts[0].toLowerCase();
            switch (command) {
                case "lb":
                    return listBundles();
                case "install":
                    if (parts.length < 2) {
                        return "usage: install <bundle-url>\n";
                    }
                    launcher.installBundle(parts[1]);
                    return "Bundle installed\n";
                case "start":
                    if (parts.length < 2) {
                        return "usage: start <bundle-id>\n";
                    }
                    long startId = Long.parseLong(parts[1]);
                    var startBundle = launcher.getBundleContext().getBundle(startId);
                    if (startBundle == null) {
                        return "ERROR: Bundle " + parts[1] + " not found\n";
                    }
                    startBundle.start();
                    return "Bundle " + parts[1] + " started\n";
                case "stop":
                    if (parts.length < 2) {
                        return "usage: stop <bundle-id>\n";
                    }
                    long stopId = Long.parseLong(parts[1]);
                    var stopBundle = launcher.getBundleContext().getBundle(stopId);
                    if (stopBundle == null) {
                        return "ERROR: Bundle " + parts[1] + " not found\n";
                    }
                    stopBundle.stop();
                    return "Bundle " + parts[1] + " stopped\n";
                case "uninstall":
                    if (parts.length < 2) {
                        return "usage: uninstall <bundle-id>\n";
                    }
                    long uninstallId = Long.parseLong(parts[1]);
                    var uninstallBundle = launcher.getBundleContext().getBundle(uninstallId);
                    if (uninstallBundle == null) {
                        return "ERROR: Bundle " + parts[1] + " not found\n";
                    }
                    uninstallBundle.uninstall();
                    return "Bundle " + parts[1] + " uninstalled\n";
                case "help":
                    return "Available commands: lb, install <url>, start <id>, stop <id>, uninstall <id>, services, help, exit\n";
                case "services":
                    return listServices();
                case "exit":
                case "quit":
                    return "";
                default:
                    return "Unknown command: " + command + ". Type 'help' for available commands.\n";
            }
        } catch (NumberFormatException e) {
            return "ERROR: invalid bundle id\n";
        } catch (Exception e) {
            log.error("[OSGi-SSH] 命令执行失败: {}", input, e);
            return "ERROR: " + e.getMessage() + "\n";
        }
    }

    private String listBundles() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-4s %-12s %-6s %s%n", "ID", "State", "Level", "Name"));
        var ctx = launcher.getBundleContext();
        for (var bundle : ctx.getBundles()) {
            String state = switch (bundle.getState()) {
                case org.osgi.framework.Bundle.ACTIVE -> "Active";
                case org.osgi.framework.Bundle.INSTALLED -> "Installed";
                case org.osgi.framework.Bundle.RESOLVED -> "Resolved";
                case org.osgi.framework.Bundle.STARTING -> "Starting";
                case org.osgi.framework.Bundle.STOPPING -> "Stopping";
                case org.osgi.framework.Bundle.UNINSTALLED -> "Uninstalled";
                default -> String.valueOf(bundle.getState());
            };
            sb.append(String.format("%-4d %-12s %-6d %s%n",
                    bundle.getBundleId(), state, 1,
                    bundle.getSymbolicName() + " (" + bundle.getVersion() + ")"));
        }
        return sb.toString();
    }

    private String listServices() {
        StringBuilder sb = new StringBuilder("Registered services:\n");
        try {
            var refs = launcher.getBundleContext().getAllServiceReferences(null, null);
            if (refs != null) {
                for (var ref : refs) {
                    sb.append("  ").append(ref.getProperty("service.id"))
                            .append(": ").append(ref.getProperty("objectClass"))
                            .append(" [bundle=").append(ref.getBundle().getBundleId()).append("]\n");
                }
            }
        } catch (Exception e) {
            sb.append("  (none)\n");
        }
        return sb.toString();
    }
}