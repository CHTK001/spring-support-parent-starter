package com.chua.starter.shell.command;

import com.chua.starter.shell.support.ConsoleColor;
import com.chua.starter.shell.support.ShellHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 日志管理命令
 * <p>
 * 提供日志等级查看和修改功能
 * 
 * @author CH
 * @version 4.0.0.37
 */
@Slf4j
@ShellComponent
@ShellCommandGroup("日志管理")
public class LogCommand {

    private final ShellHelper helper;
    private final LoggingSystem loggingSystem;

    public LogCommand(ShellHelper helper) {
        this.helper = helper;
        this.loggingSystem = new LogbackLoggingSystem(this.getClass().getClassLoader());
    }

    /**
     * 查看或修改日志等级
     * 
     * @param logger 日志记录器名称
     * @param level  日志等级
     * @return 操作结果
     */
    @ShellMethod(value = "查看或修改日志等级", key = {"log-level", "ll"})
    public String logLevel(@ShellOption(value = {"-l", "--logger"}, defaultValue = "root", help = "日志记录器名称") String logger,
                          @ShellOption(value = {"-v", "--level"}, defaultValue = "", help = "日志等级") String level) {
        
        var sb = new StringBuilder();
        
        if (level.isEmpty()) {
            // 查看当前日志等级
            var currentLevel = loggingSystem.getLoggerConfiguration(logger).getEffectiveLevel();
            sb.append(helper.getColored("当前日志等级:", ConsoleColor.CYAN)).append("\n");
            sb.append("Logger: ").append(helper.getColored(logger, ConsoleColor.YELLOW)).append("\n");
            sb.append("Level: ").append(helper.getColored(currentLevel != null ? currentLevel.name() : "INHERIT", ConsoleColor.GREEN)).append("\n");
        } else {
            // 修改日志等级
            try {
                var newLevel = LogLevel.valueOf(level.toUpperCase());
                loggingSystem.setLogLevel(logger, newLevel);

                sb.append(helper.getColored("日志等级修改成功:", ConsoleColor.GREEN)).append("\n");
                sb.append("Logger: ").append(helper.getColored(logger, ConsoleColor.YELLOW)).append("\n");
                sb.append("New Level: ").append(helper.getColored(newLevel.name(), ConsoleColor.GREEN)).append("\n");
                
                // 记录操作日志
                log.info("[Shell][日志管理] 日志等级已修改: {} -> {}", logger, newLevel);
                
            } catch (IllegalArgumentException e) {
                sb.append(helper.getColored("错误: 无效的日志等级 '" + level + "'", ConsoleColor.RED)).append("\n");
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
        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 日志配置信息 ===", ConsoleColor.CYAN)).append("\n\n");
        
        // 显示常用Logger的配置
        String[] commonLoggers = {"root", "com.chua", "org.springframework", "org.apache", "com.zaxxer.hikari"};

        sb.append(helper.getColored("常用Logger配置:", ConsoleColor.YELLOW)).append("\n");
        for (String loggerName : commonLoggers) {
            try {
                var loggerConfig = loggingSystem.getLoggerConfiguration(loggerName);
                var levelValue = loggerConfig != null ? loggerConfig.getEffectiveLevel() : null;
                sb.append(String.format("  %-25s : %s%n", loggerName,
                        levelValue != null ? helper.getColored(levelValue.name(), ConsoleColor.GREEN) : "INHERIT"));
            } catch (Exception e) {
                sb.append(String.format("  %-25s : %s%n", loggerName, "N/A"));
            }
        }

        sb.append("\n").append(helper.getColored("支持的日志等级:", ConsoleColor.YELLOW)).append("\n");
        sb.append("  ").append(Arrays.stream(LogLevel.values())
                .map(logLevel -> helper.getColored(logLevel.name(), getLogLevelColor(logLevel)))
            .collect(Collectors.joining(", "))).append("\n");

        sb.append("\n").append(helper.getColored("使用说明:", ConsoleColor.GREEN)).append("\n");
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

            var sb = helper.getColored("日志等级重置成功:", ConsoleColor.GREEN) + "\n" +
                    "Logger: " + helper.getColored(logger, ConsoleColor.YELLOW) + "\n" +
                    "已重置为继承父级配置\n";
            
            // 记录操作日志
            log.info("[Shell][日志管理] 日志等级已重置: {}", logger);
            
            return sb;
            
        } catch (Exception e) {
            return helper.getColored("重置失败: " + e.getMessage(), ConsoleColor.RED);
        }
    }

    /**
     * 获取日志等级对应的颜色
     * 
     * @param level 日志等级
     * @return 颜色
     */
    private ConsoleColor getLogLevelColor(LogLevel level) {
        return switch (level) {
            case TRACE, DEBUG -> ConsoleColor.BLUE;
            case INFO -> ConsoleColor.GREEN;
            case WARN -> ConsoleColor.YELLOW;
            case ERROR -> ConsoleColor.RED;
            default -> ConsoleColor.WHITE;
        };
    }
}
