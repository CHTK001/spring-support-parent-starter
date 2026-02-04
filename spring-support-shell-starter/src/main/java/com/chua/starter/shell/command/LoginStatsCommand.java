package com.chua.starter.shell.command;

import com.chua.starter.shell.support.ConsoleColor;
import com.chua.starter.shell.support.ShellHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 登录统计命令
 * <p>
 * 提供登录统计信息查询功能
 *
 * @author CH
 * @version 4.0.0.37
 */
@Slf4j
@ShellComponent
@ShellCommandGroup("登录统计")
public class LoginStatsCommand {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ShellHelper helper;

    public LoginStatsCommand(ShellHelper helper) {
        this.helper = helper;
    }

    /**
     * 显示登录统计信息
     *
     * @param days 统计天数
     * @param top  显示前N条
     * @return 统计信息
     */
    @ShellMethod(value = "显示登录统计信息", key = {"login-stats", "ls"})
    public String loginStats(@ShellOption(value = {"-d", "--days"}, defaultValue = "7", help = "统计天数") int days,
                             @ShellOption(value = {"-t", "--top"}, defaultValue = "10", help = "显示前N条") int top) {

        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 登录统计信息 ===", ConsoleColor.CYAN)).append("\n\n");
        sb.append("统计周期: 最近 ").append(helper.getColored(String.valueOf(days), ConsoleColor.YELLOW)).append(" 天\n");
        sb.append("查询时间: ").append(helper.getColored(LocalDateTime.now().format(DATE_TIME_FORMATTER), ConsoleColor.GREEN)).append("\n\n");

        // 模拟统计数据
        sb.append(helper.getColored("概要统计:", ConsoleColor.YELLOW)).append("\n");
        sb.append(String.format("  总登录次数:     %s%n", helper.getColored("1,234", ConsoleColor.GREEN)));
        sb.append(String.format("  独立用户数:     %s%n", helper.getColored("156", ConsoleColor.GREEN)));
        sb.append(String.format("  独立IP数:       %s%n", helper.getColored("89", ConsoleColor.GREEN)));
        sb.append(String.format("  成功率:         %s%n", helper.getColored("98.5%", ConsoleColor.GREEN)));
        sb.append(String.format("  失败次数:       %s%n", helper.getColored("19", ConsoleColor.RED)));

        sb.append("\n").append(helper.getColored("Top " + top + " 活跃用户:", ConsoleColor.YELLOW)).append("\n");
        sb.append(helper.separator()).append("\n");
        sb.append(String.format("%-5s %-20s %-10s %-20s%n", "排名", "用户名", "登录次数", "最后登录时间"));
        sb.append(helper.separator()).append("\n");

        // 模拟数据
        String[][] users = {
                {"1", "admin", "156", "2026-02-04 10:30:00"},
                {"2", "zhangsan", "89", "2026-02-04 09:15:00"},
                {"3", "lisi", "67", "2026-02-03 18:45:00"},
                {"4", "wangwu", "45", "2026-02-03 14:20:00"},
                {"5", "zhaoliu", "34", "2026-02-02 16:30:00"}
        };

        for (String[] user : users) {
            sb.append(String.format("%-5s %-20s %-10s %-20s%n", user[0], user[1], user[2], user[3]));
        }

        sb.append(helper.separator()).append("\n");
        sb.append("\n").append(helper.getColored("提示:", ConsoleColor.GREEN))
                .append(" 使用 'login-history -u <用户名>' 查看详细登录记录\n");

        return sb.toString();
    }

    /**
     * 显示登录历史记录
     *
     * @param username 用户名
     * @param limit    限制数量
     * @return 登录历史
     */
    @ShellMethod(value = "显示登录历史记录", key = {"login-history", "lh"})
    public String loginHistory(@ShellOption(value = {"-u", "--user"}, defaultValue = "", help = "用户名") String username,
                               @ShellOption(value = {"-n", "--limit"}, defaultValue = "20", help = "显示条数") int limit) {

        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 登录历史记录 ===", ConsoleColor.CYAN)).append("\n\n");

        if (!username.isEmpty()) {
            sb.append("筛选用户: ").append(helper.getColored(username, ConsoleColor.YELLOW)).append("\n\n");
        }

        sb.append(helper.separator(80)).append("\n");
        sb.append(String.format("%-5s %-15s %-18s %-20s %-8s%n", "序号", "用户名", "IP地址", "登录时间", "状态"));
        sb.append(helper.separator(80)).append("\n");

        // 模拟数据
        String[][] records = {
                {"1", "admin", "192.168.1.100", "2026-02-04 10:30:00", "成功"},
                {"2", "admin", "192.168.1.101", "2026-02-04 09:15:00", "成功"},
                {"3", "zhangsan", "10.0.0.50", "2026-02-04 08:45:00", "成功"},
                {"4", "lisi", "172.16.0.20", "2026-02-03 18:30:00", "失败"},
                {"5", "admin", "192.168.1.100", "2026-02-03 17:20:00", "成功"}
        };

        for (String[] record : records) {
            var status = "成功".equals(record[4]) ?
                    helper.getColored(record[4], ConsoleColor.GREEN) :
                    helper.getColored(record[4], ConsoleColor.RED);
            sb.append(String.format("%-5s %-15s %-18s %-20s %s%n",
                    record[0], record[1], record[2], record[3], status));
        }

        sb.append(helper.separator(80)).append("\n");
        sb.append("共显示 ").append(helper.getColored(String.valueOf(records.length), ConsoleColor.YELLOW))
                .append(" 条记录\n");

        return sb.toString();
    }

    /**
     * 显示当前活跃会话
     *
     * @return 活跃会话列表
     */
    @ShellMethod(value = "显示当前活跃会话", key = {"active-sessions", "as"})
    public String activeSessions() {
        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 当前活跃会话 ===", ConsoleColor.CYAN)).append("\n\n");
        sb.append("查询时间: ").append(helper.getColored(LocalDateTime.now().format(DATE_TIME_FORMATTER), ConsoleColor.GREEN)).append("\n\n");

        sb.append(helper.separator(90)).append("\n");
        sb.append(String.format("%-10s %-15s %-18s %-20s %-15s%n",
                "会话ID", "用户名", "IP地址", "登录时间", "在线时长"));
        sb.append(helper.separator(90)).append("\n");

        // 模拟数据
        String[][] sessions = {
                {"a1b2c3d4", "admin", "192.168.1.100", "2026-02-04 08:00:00", "2小时30分"},
                {"e5f6g7h8", "zhangsan", "10.0.0.50", "2026-02-04 09:15:00", "1小时15分"},
                {"i9j0k1l2", "lisi", "172.16.0.20", "2026-02-04 10:00:00", "30分钟"}
        };

        for (String[] session : sessions) {
            sb.append(String.format("%-10s %-15s %-18s %-20s %-15s%n",
                    session[0], session[1], session[2], session[3], session[4]));
        }

        sb.append(helper.separator(90)).append("\n");
        sb.append("当前活跃会话数: ").append(helper.getColored(String.valueOf(sessions.length), ConsoleColor.YELLOW)).append("\n");

        return sb.toString();
    }

    /**
     * 显示IP统计信息
     *
     * @param ip IP地址
     * @return IP统计信息
     */
    @ShellMethod(value = "显示IP统计信息", key = {"ip-stats", "is"})
    public String ipStats(@ShellOption(value = {"-i", "--ip"}, defaultValue = "", help = "IP地址") String ip) {

        var sb = new StringBuilder();

        sb.append(helper.getColored("=== IP统计信息 ===", ConsoleColor.CYAN)).append("\n\n");

        if (!ip.isEmpty()) {
            sb.append("查询IP: ").append(helper.getColored(ip, ConsoleColor.YELLOW)).append("\n\n");
            sb.append("登录次数: ").append(helper.getColored("45", ConsoleColor.GREEN)).append("\n");
            sb.append("关联用户: ").append(helper.getColored("admin, zhangsan", ConsoleColor.GREEN)).append("\n");
            sb.append("首次登录: ").append(helper.getColored("2026-01-01 10:00:00", ConsoleColor.GREEN)).append("\n");
            sb.append("最后登录: ").append(helper.getColored("2026-02-04 10:30:00", ConsoleColor.GREEN)).append("\n");
            sb.append("地理位置: ").append(helper.getColored("中国 北京市", ConsoleColor.GREEN)).append("\n");
        } else {
            sb.append(helper.getColored("Top 10 登录IP:", ConsoleColor.YELLOW)).append("\n");
            sb.append(helper.separator(70)).append("\n");
            sb.append(String.format("%-5s %-18s %-10s %-20s %-15s%n",
                    "排名", "IP地址", "登录次数", "最后登录时间", "地理位置"));
            sb.append(helper.separator(70)).append("\n");

            String[][] ips = {
                    {"1", "192.168.1.100", "156", "2026-02-04 10:30:00", "北京"},
                    {"2", "10.0.0.50", "89", "2026-02-04 09:15:00", "上海"},
                    {"3", "172.16.0.20", "67", "2026-02-03 18:45:00", "广州"}
            };

            for (String[] ipInfo : ips) {
                sb.append(String.format("%-5s %-18s %-10s %-20s %-15s%n",
                        ipInfo[0], ipInfo[1], ipInfo[2], ipInfo[3], ipInfo[4]));
            }

            sb.append(helper.separator(70)).append("\n");
        }

        return sb.toString();
    }
}

