package com.chua.starter.shell.command;

import com.github.fonimus.ssh.shell.PromptColor;
import com.github.fonimus.ssh.shell.SshShellHelper;
import com.github.fonimus.ssh.shell.commands.SshShellComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 日志管理命令
 * 
 * 提供日志等级查看和修改功能
 * 
 * @author CH
 * @version 4.0.0.32
 */
@Slf4j
@SshShellComponent
@ShellCommandGroup("日志管理")
public class LogCommand {

    private final SshShellHelper helper;
    private final LoggingSystem loggingSystem;

    public LogCommand(SshShellHelper helper) {
        this.helper = helper;
        this.loggingSystem = new LogbackLoggingSystem(this.getClass().getClassLoader());
    }

    /**
     * 查看或修改日志等级
     * 
     * @param logger 日志记录器名称
     * @param level 日志等级
     * @return 操作结果
     */
    @ShellMethod(value = "查看或修改日志等级", key = {"log-level", "ll"})
    public String logLevel(@ShellOption(value = {"-l", "--logger"}, defaultValue = "root", help = "日志记录器名称") String logger,
                          @ShellOption(value = {"-v", "--level"}, defaultValue = "", help = "日志等级") String level) {
        
        StringBuilder sb = new StringBuilder();
        
        if (level.isEmpty()) {
            // 查看当前日志等级
            LogLevel currentLevel = loggingSystem.getLoggerConfiguration(logger).getEffectiveLevel();
            sb.append(helper.getColored("当前日志等级:", PromptColor.CYAN)).append("\n");
            sb.append("Logger: ").append(helper.getColored(logger, PromptColor.YELLOW)).append("\n");
            sb.append("Level: ").append(helper.getColored(currentLevel != null ? currentLevel.name() : "INHERIT", PromptColor.GREEN)).append("\n");
        } else {
            // 修改日志等级
            try {
                LogLevel newLevel = LogLevel.valueOf(level.toUpperCase());
                loggingSystem.setLogLevel(logger, newLevel);

                sb.append(helper.getColored("日志等级修改成功:", PromptColor.GREEN)).append("\n");
                sb.append("Logger: ").append(helper.getColored(logger, PromptColor.YELLOW)).append("\n");
                sb.append("New Level: ").append(helper.getColored(newLevel.name(), PromptColor.GREEN)).append("\n");
                
                // 记录操作日志
                log.info("日志等级已修改: {} -> {}", logger, newLevel);
                
            } catch (IllegalArgumentException e) {
                sb.append(helper.getColored("错误: 无效的日志等级 '" + level + "'", PromptColor.RED)).append("\n");
                sb.append("支持的日志等级: ").append(
                    Arrays.stream(LogLevel.values())
                        .map(LogLevel::name)
                        .collect(Collectors.joining(", "))
                ).append("\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * 显示日志配置信息
     * 
     * @return 日志配置信息
     */
    @ShellMethod(value = "显示日志配置信息", key = {"log-info", "li"})
    public String logInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append(helper.getColored("=== 日志配置信息 ===", PromptColor.CYAN)).append("\n\n");
        
        // 显示常用Logger的配置
        String[] commonLoggers = {"root", "com.chua", "org.springframework", "org.apache", "com.zaxxer.hikari"};

        sb.append(helper.getColored("常用Logger配置:", PromptColor.YELLOW)).append("\n");
        for (String loggerName : commonLoggers) {
            try {
                LogLevel level = loggingSystem.getLoggerConfiguration(loggerName).getEffectiveLevel();
                sb.append(String.format("  %-25s : %s\n", loggerName,
                        level != null ? helper.getColored(level.name(), PromptColor.GREEN) : "INHERIT"));
            } catch (Exception e) {
                sb.append(String.format("  %-25s : %s\n", loggerName, "N/A"));
            }
        }

        sb.append("\n").append(helper.getColored("支持的日志等级:", PromptColor.YELLOW)).append("\n");
        sb.append("  ").append(Arrays.stream(LogLevel.values())
            .map(level -> helper.getColored(level.name(), getLogLevelColor(level)))
            .collect(Collectors.joining(", "))).append("\n");

        sb.append("\n").append(helper.getColored("使用说明:", PromptColor.GREEN)).append("\n");
        sb.append("- 使用 'log-level <logger> <level>' 修改日志等级\n");
        sb.append("- 使用 'log-level <logger>' 查看特定logger的等级\n");
        sb.append("- 日志等级从低到高: TRACE < DEBUG < INFO < WARN < ERROR\n");
        
        return sb.toString();
    }

    /**
     * 重置日志等级
     * 
     * @param logger 日志记录器名称
     * @return 操作结果
     */
    @ShellMethod(value = "重置日志等级为默认值", key = {"log-reset", "lr"})
    public String logReset(@ShellOption(value = {"-l", "--logger"}, defaultValue = "root", help = "日志记录器名称") String logger) {
        
        try {
            // 重置为null，让其继承父级配置
            loggingSystem.setLogLevel(logger, null);

            String sb = helper.getColored("日志等级重置成功:", PromptColor.GREEN) + "\n" +
                    "Logger: " + helper.getColored(logger, PromptColor.YELLOW) + "\n" +
                    "已重置为继承父级配置\n";
            
            // 记录操作日志
            log.info("日志等级已重置: {}", logger);
            
            return sb;
            
        } catch (Exception e) {
            return helper.getColored("重置失败: " + e.getMessage(), PromptColor.RED);
        }
    }

    /**
     * 获取日志等级对应的颜色
     * 
     * @param level 日志等级
     * @return 颜色
     */
    private PromptColor getLogLevelColor(LogLevel level) {
        return switch (level) {
            case TRACE, DEBUG -> PromptColor.BLUE;
            case INFO -> PromptColor.GREEN;
            case WARN -> PromptColor.YELLOW;
            case ERROR -> PromptColor.RED;
            default -> PromptColor.WHITE;
        };
    }
}
