package com.chua.starter.redis.support.utils;

import java.util.UUID;

/**
 * ID工具类
 *
 * @author CH
 * @since 2024/12/25
 */
public final class IdUtils {

    private IdUtils() {
    }

    /**
     * 生成UUID字符串（去除横线）
     *
     * @return UUID字符串，不包含横线
     */
    public static String createId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成UUID字符串（包含横线）
     *
     * @return UUID字符串，包含横线
     */
    public static String createIdWithDash() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成简单的UUID字符串（去除横线）
     *
     * @return UUID字符串，不包含横线
     */
    public static String createSimpleUuid() {
        return createId();
    }
}

