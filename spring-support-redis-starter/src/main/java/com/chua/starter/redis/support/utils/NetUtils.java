package com.chua.starter.redis.support.utils;

import java.io.IOException;
import java.net.Socket;

/**
 * 网络工具类
 *
 * @author CH
 * @since 2024/12/25
 */
public final class NetUtils {

    private NetUtils() {
    }

    /**
     * 检查端口是否被占用
     *
     * @param port 端口号
     * @return 如果端口被占用返回 true，否则返回 false
     */
    public static boolean isPortInUsed(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

