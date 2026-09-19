package com.chua.starter.redis.support.utils;

import java.io.IOException;
import java.net.Socket;

/**
 * 网络工具类
 *
 * @since 2024/12/25
 * @author CH
 */
public final class NetUtils {

    /** 创建 net工具 实例 */
    private NetUtils() {
    }

    /**
    * 检查端口是否被占用
    *
    * @param port 端口号
    * @return 如果端口被占用返回 true，否则返回 false
    */
    public static boolean isPortInUsed(int port) {
        /**
         * 尝试。
         * @param port 端口
         */
        try (Socket socket = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

