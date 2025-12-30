package com.chua.starter.rust.server.native_;

import com.sun.jna.*;

/**
 * JNA 接口用于调用 Rust 动态库
 *
 * @author CH
 * @since 4.0.0
 */
public interface RustNative extends Library {

    /**
     * 回调接口：Rust 调用此接口处理 HTTP 请求
     */
    interface RequestCallback extends Callback {
        /**
         * 处理 HTTP 请求
         *
         * @param requestId    请求 ID
         * @param method       HTTP 方法
         * @param uri          请求 URI
         * @param headersJson  请求头 JSON 字符串
         * @param bodyData     请求体数据
         * @param bodyLen      请求体长度
         * @param userData     用户数据指针
         * @return 0 表示成功，非 0 表示失败
         */
        int callback(
                long requestId,
                String method,
                String uri,
                String headersJson,
                Pointer bodyData,
                long bodyLen,
                Pointer userData
        );
    }

    /**
     * 启动 HTTP 服务器
     *
     * @param host     监听地址
     * @param port     监听端口
     * @param callback 请求回调函数
     * @param userData 用户数据指针
     * @return 0 表示成功，-1 表示失败
     */
    int rust_server_start(String host, short port, RequestCallback callback, Pointer userData);

    /**
     * 停止 HTTP 服务器
     *
     * @return 0 表示成功，-1 表示失败
     */
    int rust_server_stop();

    /**
     * 发送 HTTP 响应
     *
     * @param requestId   请求 ID
     * @param status      HTTP 状态码
     * @param headersJson 响应头 JSON 字符串
     * @param bodyData    响应体数据
     * @param bodyLen     响应体长度
     * @return 0 表示成功，-1 表示失败
     */
    int rust_server_send_response(
            long requestId,
            short status,
            String headersJson,
            Pointer bodyData,
            long bodyLen
    );
}
