package com.chua.starter.shell.support;

import org.springframework.stereotype.Component;

/**
 * Shell辅助工具类
 * <p>
 * 提供Shell命令输出的着色和格式化功能
 *
 * @author CH
 * @since 2026/02/04
 */
@Component
public class ShellHelper {

    private boolean colorEnabled = true;

    /**
     * 获取着色后的文本
     *
     * @param text  文本内容
     * @param color 颜色
     * @return 着色后的文本
     */
    public String getColored(String text, ConsoleColor color) {
        if (!colorEnabled || color == null) {
            return text != null ? text : "";
        }
        return color.colorize(text != null ? text : "");
    }

    /**
     * 获取成功样式文本（绿色）
     *
     * @param text 文本内容
     * @return 着色后的文本
     */
    public String success(String text) {
        return getColored(text, ConsoleColor.GREEN);
    }

    /**
     * 获取警告样式文本（黄色）
     *
     * @param text 文本内容
     * @return 着色后的文本
     */
    public String warning(String text) {
        return getColored(text, ConsoleColor.YELLOW);
    }

    /**
     * 获取错误样式文本（红色）
     *
     * @param text 文本内容
     * @return 着色后的文本
     */
    public String error(String text) {
        return getColored(text, ConsoleColor.RED);
    }

    /**
     * 获取信息样式文本（青色）
     *
     * @param text 文本内容
     * @return 着色后的文本
     */
    public String info(String text) {
        return getColored(text, ConsoleColor.CYAN);
    }

    /**
     * 获取提示样式文本（蓝色）
     *
     * @param text 文本内容
     * @return 着色后的文本
     */
    public String highlight(String text) {
        return getColored(text, ConsoleColor.BLUE);
    }

    /**
     * 设置是否启用颜色输出
     *
     * @param colorEnabled 是否启用
     */
    public void setColorEnabled(boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }

    /**
     * 检查颜色输出是否启用
     *
     * @return 是否启用
     */
    public boolean isColorEnabled() {
        return colorEnabled;
    }

    /**
     * 打印分隔线
     *
     * @param length 长度
     * @return 分隔线
     */
    public String separator(int length) {
        return "-".repeat(Math.max(0, length));
    }

    /**
     * 打印分隔线（默认60字符）
     *
     * @return 分隔线
     */
    public String separator() {
        return separator(60);
    }
}

