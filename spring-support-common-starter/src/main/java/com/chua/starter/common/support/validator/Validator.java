package com.chua.starter.common.support.validator;

/**
 * 简单校验器。
 */
public final class Validator {

    private Validator() {
    }

    public static boolean hasChinese(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (c >= '\u4E00' && c <= '\u9FFF') {
                return true;
            }
        }
        return false;
    }
}
