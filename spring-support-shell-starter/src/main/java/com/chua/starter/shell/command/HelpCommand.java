package com.chua.starter.shell.command;

import com.github.fonimus.ssh.shell.SshShellHelper;
import com.github.fonimus.ssh.shell.commands.SshShellComponent;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * 帮助命令
 * 
 * 提供系统帮助信息和命令说明
 * 
 * @author CH
 * @version 4.0.0.32
 */
@SshShellComponent
@ShellCommandGroup("系统命令")
public class HelpCommand {

    private final SshShellHelper helper;

    public HelpCommand(SshShellHelper helper) {
        this.helper = helper;
    }

    /**
     * 显示系统帮助信息
     * 
     * @param command 可选的具体命令名称
     * @return 帮助信息
     */
    @ShellMethod(value = "显示帮助信息", key = {"help", "h", "?"})
    public String help(@ShellOption(value = {"-c", "--command"}, defaultValue = "", help = "显示特定命令的帮助") String command) {
        StringBuilder sb = new StringBuilder();
        
        if (command.isEmpty()) {
            // 显示总体帮助信息
            sb.append(helper.getColored("=== Spring Support Shell 帮助系统 ===", SshShellHelper.Color.CYAN)).append("\n\n");
            
            sb.append(helper.getColored("系统命令:", SshShellHelper.Color.YELLOW)).append("\n");
            sb.append("  help, h, ?           - 显示帮助信息\n");
            sb.append("  exit, quit           - 退出Shell\n");
            sb.append("  clear                - 清屏\n");
            sb.append("  history              - 显示命令历史\n\n");
            
            sb.append(helper.getColored("日志管理命令:", SshShellHelper.Color.YELLOW)).append("\n");
            sb.append("  log-level            - 查看或修改日志等级\n");
            sb.append("  log-info             - 显示日志配置信息\n\n");
            
            sb.append(helper.getColored("登录统计命令:", SshShellHelper.Color.YELLOW)).append("\n");
            sb.append("  login-stats          - 显示登录统计信息\n");
            sb.append("  login-history        - 显示登录历史记录\n");
            sb.append("  active-sessions      - 显示当前活跃会话\n\n");
            
            sb.append(helper.getColored("系统监控命令:", SshShellHelper.Color.YELLOW)).append("\n");
            sb.append("  system-info          - 显示系统信息\n");
            sb.append("  memory-info          - 显示内存使用情况\n");
            sb.append("  thread-info          - 显示线程信息\n\n");
            
            sb.append(helper.getColored("使用提示:", SshShellHelper.Color.GREEN)).append("\n");
            sb.append("- 使用 Tab 键可以自动补全命令\n");
            sb.append("- 使用 help <命令名> 可以查看具体命令的详细帮助\n");
            sb.append("- 使用 Ctrl+C 可以中断当前命令\n");
            sb.append("- 使用 Ctrl+D 或 exit 可以退出Shell\n");
            
        } else {
            // 显示特定命令的帮助
            sb.append(getCommandHelp(command));
        }
        
        return sb.toString();
    }

    /**
     * 获取特定命令的帮助信息
     * 
     * @param command 命令名称
     * @return 命令帮助信息
     */
    private String getCommandHelp(String command) {
        return switch (command.toLowerCase()) {
            case "log-level" -> helper.getColored("log-level 命令帮助:", SshShellHelper.Color.CYAN) + "\n" +
                    "用法: log-level [logger] [level]\n" +
                    "参数:\n" +
                    "  logger  - 日志记录器名称（可选，默认为root）\n" +
                    "  level   - 日志等级（可选，不指定则显示当前等级）\n" +
                    "支持的日志等级: TRACE, DEBUG, INFO, WARN, ERROR, OFF\n" +
                    "示例:\n" +
                    "  log-level                    - 显示root日志等级\n" +
                    "  log-level DEBUG              - 设置root日志等级为DEBUG\n" +
                    "  log-level com.chua INFO      - 设置com.chua包的日志等级为INFO";
                    
            case "login-stats" -> helper.getColored("login-stats 命令帮助:", SshShellHelper.Color.CYAN) + "\n" +
                    "用法: login-stats [options]\n" +
                    "选项:\n" +
                    "  -d, --days <天数>    - 显示指定天数内的统计（默认7天）\n" +
                    "  -i, --ip <IP地址>    - 显示特定IP的统计信息\n" +
                    "  -t, --top <数量>     - 显示登录次数最多的前N个IP（默认10）\n" +
                    "示例:\n" +
                    "  login-stats                  - 显示最近7天的登录统计\n" +
                    "  login-stats -d 30            - 显示最近30天的登录统计\n" +
                    "  login-stats -i 192.168.1.100 - 显示特定IP的登录统计";
                    
            case "system-info" -> helper.getColored("system-info 命令帮助:", SshShellHelper.Color.CYAN) + "\n" +
                    "用法: system-info [type]\n" +
                    "参数:\n" +
                    "  type - 信息类型（可选）\n" +
                    "支持的类型: os, jvm, memory, disk, network\n" +
                    "示例:\n" +
                    "  system-info          - 显示所有系统信息\n" +
                    "  system-info os       - 只显示操作系统信息\n" +
                    "  system-info memory   - 只显示内存信息";
                    
            default -> helper.getColored("未知命令: " + command, SshShellHelper.Color.RED) + "\n" +
                    "使用 'help' 查看所有可用命令";
        };
    }

    /**
     * 显示版本信息
     * 
     * @return 版本信息
     */
    @ShellMethod(value = "显示版本信息", key = {"version", "v"})
    public String version() {
        StringBuilder sb = new StringBuilder();
        sb.append(helper.getColored("Spring Support Shell Starter", SshShellHelper.Color.CYAN)).append("\n");
        sb.append("版本: 4.0.0.32\n");
        sb.append("构建时间: ").append(java.time.LocalDateTime.now().toString()).append("\n");
        sb.append("Java版本: ").append(System.getProperty("java.version")).append("\n");
        sb.append("Spring Boot版本: ").append(org.springframework.boot.SpringBootVersion.getVersion()).append("\n");
        return sb.toString();
    }
}
