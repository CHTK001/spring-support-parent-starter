package com.chua.starter.shell.support;

/**
 * 控制台颜色枚举
 * <p>
 * 用于Shell命令输出着色
 *
 * @author CH
 * @since 2026/02/04
 */
public enum ConsoleColor {
    
    /**
     * 黑色
     */
    BLACK("\u001B[30m"),
    
    /**
     * 红色
     */
    RED("\u001B[31m"),
    
    /**
     * 绿色
     */
    GREEN("\u001B[32m"),
    
    /**
     * 黄色
     */
    YELLOW("\u001B[33m"),
    
    /**
     * 蓝色
     */
    BLUE("\u001B[34m"),
    
    /**
     * 紫色
     */
    MAGENTA("\u001B[35m"),
    
    /**
     * 青色
     */
    CYAN("\u001B[36m"),
    
    /**
     * 白色
     */
    WHITE("\u001B[37m"),
    
    /**
     * 亮黑色（灰色）
     */
    BRIGHT_BLACK("\u001B[90m"),
    
    /**
     * 亮红色
     */
    BRIGHT_RED("\u001B[91m"),
    
    /**
     * 亮绿色
     */
    BRIGHT_GREEN("\u001B[92m"),
    
    /**
     * 亮黄色
     */
    BRIGHT_YELLOW("\u001B[93m"),
    
    /**
     * 亮蓝色
     */
    BRIGHT_BLUE("\u001B[94m"),
    
    /**
     * 亮紫色
     */
    BRIGHT_MAGENTA("\u001B[95m"),
    
    /**
     * 亮青色
     */
    BRIGHT_CYAN("\u001B[96m"),
    
    /**
     * 亮白色
     */
    BRIGHT_WHITE("\u001B[97m");

    private static final String RESET = "\u001B[0m";
    private final String code;

    ConsoleColor(String code) {
        this.code = code;
    }

    /**
     * 获取颜色代码
     *
     * @return 颜色ANSI代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取重置代码
     *
     * @return 重置ANSI代码
     */
    public static String getReset() {
        return RESET;
    }

    /**
     * 给文本着色
     *
     * @param text 文本内容
     * @return 着色后的文本
     */
    public String colorize(String text) {
        return code + text + RESET;
    }
}

