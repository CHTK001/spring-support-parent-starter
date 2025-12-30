package com.chua.starter.rust.server.ipc;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Named Pipe / TCP Socket IPC 通道实现
 * <p>
 * Windows 上使用 TCP Socket (Named Pipe 在 Java 中不易实现)
 * Linux/Mac 上使用 Unix Domain Socket (通过 TCP 模拟)
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class NamedPipeChannel implements IpcChannel {

    private final String name;
    private final int bufferSize;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    private final BlockingQueue<RequestMessage> requestQueue = new LinkedBlockingQueue<>();
    private Thread acceptThread;

    public NamedPipeChannel(int bufferSize) {
        this.name = "rust-ipc-" + UUID.randomUUID().toString().substring(0, 8);
        this.bufferSize = bufferSize;
    }

    @Override
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            // 使用随机端口的 TCP Socket
            serverSocket = new ServerSocket(0);
            serverSocket.setReceiveBufferSize(bufferSize);

            log.info("[Rust IPC] 通道已启动: {} (port: {})", name, serverSocket.getLocalPort());

            // 启动接受连接的线程
            acceptThread = new Thread(this::acceptLoop, "rust-ipc-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }
    }

    private void acceptLoop() {
        try {
            log.info("[Rust IPC] 等待 Rust 进程连接...");
            clientSocket = serverSocket.accept();
            clientSocket.setTcpNoDelay(true);
            clientSocket.setSendBufferSize(bufferSize);
            clientSocket.setReceiveBufferSize(bufferSize);

            inputStream = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream(), bufferSize));
            outputStream = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream(), bufferSize));

            log.info("[Rust IPC] Rust 进程已连接: {}", clientSocket.getRemoteSocketAddress());

            // 读取请求循环
            readLoop();
        } catch (IOException e) {
            if (running.get()) {
                log.error("[Rust IPC] 连接错误", e);
            }
        }
    }

    private void readLoop() {
        while (running.get() && clientSocket != null && !clientSocket.isClosed()) {
            try {
                // 读取消息长度 (4 字节, 小端序)
                byte[] lenBytes = new byte[4];
                inputStream.readFully(lenBytes);
                int length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

                if (length <= 0 || length > bufferSize * 10) {
                    log.warn("[Rust IPC] 无效的消息长度: {}", length);
                    continue;
                }

                // 读取消息体
                byte[] data = new byte[length];
                inputStream.readFully(data);

                // 解析请求消息
                RequestMessage request = RequestMessage.fromBytes(data);
                requestQueue.offer(request);

            } catch (EOFException e) {
                if (running.get()) {
                    log.info("[Rust IPC] 连接已关闭");
                }
                break;
            } catch (IOException e) {
                if (running.get()) {
                    log.error("[Rust IPC] 读取消息失败", e);
                }
                break;
            }
        }
    }

    @Override
    public RequestMessage readRequest() throws IOException {
        try {
            return requestQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public synchronized void writeResponse(ResponseMessage response) throws IOException {
        if (outputStream == null) {
            throw new IOException("IPC 通道未连接");
        }

        byte[] data = response.toBytes();

        // 写入消息长度 (4 字节, 小端序)
        byte[] lenBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(data.length)
                .array();

        outputStream.write(lenBytes);
        outputStream.write(data);
        outputStream.flush();
    }

    @Override
    public boolean isOpen() {
        return running.get() && clientSocket != null && !clientSocket.isClosed();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAddress() {
        // 返回 TCP 地址供 Rust 进程连接
        return "tcp://127.0.0.1:" + serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        if (running.compareAndSet(true, false)) {
            log.info("[Rust IPC] 关闭通道: {}", name);

            if (acceptThread != null) {
                acceptThread.interrupt();
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {}
            }

            if (clientSocket != null) {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {}
            }

            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
