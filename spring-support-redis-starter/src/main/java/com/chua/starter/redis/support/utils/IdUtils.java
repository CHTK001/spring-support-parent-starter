package com.chua.starter.redis.support.utils;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ID工具类
 *
 * @since 2024/12/25
 */
public final class IdUtils {

    /** 创建 IdUtils 实例 */
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

    /**
     * 生成 UUIDv7 字符串（RFC 9562）。
     *
     * <p>UUIDv7 格式：{@code xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx}</p>
     * <ul>
     *   <li>前48位：毫秒级时间戳（确保时间有序）</li>
     *   <li>第13位：版本号（固定为7）</li>
     *   <li>第17位：变体（固定为10xx，即8/9/a/b）</li>
     * </ul>
     *
     * @return 36 位 canonical lowercase UUIDv7 字符串
     */
    public static String createUuidV7() {
        long millis = System.currentTimeMillis();
        byte[] bytes = new byte[16];

        // 先生成随机数填充
        ThreadLocalRandom.current().nextBytes(bytes);

        // 前6字节：时间戳（大端序）
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;

        // 设置版本号为7（第6字节高4位）
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);

        // 设置变体为10xx（第8字节高2位）
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        // 格式化为字符串
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-');
            sb.append(hex[(bytes[i] >> 4) & 0xF]).append(hex[bytes[i] & 0xF]);
        }
        return sb.toString();
    }
}

