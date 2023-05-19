package com.chua.shell.support;

import com.google.common.base.Strings;

/**
 * LineUtils
 *
 * @author CH
 */
public class LineUtils {
    public static String ifx(String name, int commandLimit) {
        if (name.length() > commandLimit) {
            return name.substring(0, commandLimit - 3) + "...";
        }

        return name + Strings.repeat(" ", commandLimit - name.length());
    }
}
