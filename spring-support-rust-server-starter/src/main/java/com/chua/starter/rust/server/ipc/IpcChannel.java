package com.chua.starter.rust.server.ipc;

import java.io.Closeable;
import java.io.IOException;

/**
 * IPC 通道抽象接口
 * <p>
 * 定义 Java 与 Rust 进程间通信的接口。
 * 支持多种实现：Named Pipe (Windows), Unix Socket (Linux/Mac), TCP
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public interface IpcChannel extends Closeable {

    /**
     * 读取请求消息
     *
     * @return 请求消息，如果通道已关闭返回 null
     * @throws IOException 读取失败
     */
    RequestMessage readRequest() throws IOException;

    /**
     * 写入响应消息
     *
     * @param response 响应消息
     * @throws IOException 写入失败
     */
    void writeResponse(ResponseMessage response) throws IOException;

    /**
     * 检查通道是否已打开
     *
     * @return 是否已打开
     */
    boolean isOpen();

    /**
     * 获取通道名称 (用于日志)
     *
     * @return 通道名称
     */
    String getName();

    /**
     * 启动通道 (开始监听)
     *
     * @throws IOException 启动失败
     */
    void start() throws IOException;

    /**
     * 获取 IPC 地址 (用于传递给 Rust 进程)
     *
     * @return IPC 地址
     */
    String getAddress();
}
