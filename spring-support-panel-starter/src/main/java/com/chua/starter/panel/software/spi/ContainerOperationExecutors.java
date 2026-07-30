package com.chua.starter.panel.software.spi;

public final class ContainerOperationExecutors {
    private ContainerOperationExecutors() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static String escapeBatch(String input) {
        if (input == null) return "";
        return input.replace("%", "%%").replace("!", "^!");
    }

    public static String shellEscape(String input) {
        if (input == null) return "";
        return input.replace("'", "'\\''").replace(" ", "\\ ");
    }

    public static String maskPasswords(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)(password|passwd|pwd)=[^\\s&|;]+", "$1=****");
    }
}
