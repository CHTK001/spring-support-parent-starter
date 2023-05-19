package com.chua.shell.support;

/**
 * 反编译
 *
 * @author CH
 */
public interface Decompiler {
    /**
     * 反编译
     *
     * @param classFilePath   文件路径
     * @param methodName      方法名
     * @param hideUnicode     unicode
     * @param printLineNumber is print
     * @return 反编译
     */
    String decompile(String classFilePath, String methodName, boolean hideUnicode,
                     boolean printLineNumber);
}
