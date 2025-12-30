package com.chua.starter.rust.server.server;

import com.chua.starter.rust.server.native_.RustNativeFFM;
import com.chua.starter.rust.server.properties.RustServerProperties;
import com.chua.starter.rust.server.servlet.RustHttpServletRequest;
import com.chua.starter.rust.server.servlet.RustHttpServletResponse;
import com.chua.starter.rust.server.servlet.RustServletContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rust HTTP Web Server 实现（使用 Java 21 FFM API）
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class RustWebServer implements WebServer {

    private final RustServerProperties properties;
    private final int port;
    private final String address;
    private final ServletContextInitializer[] initializers;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private RustServletContext servletContext;
    private Servlet dispatcherServlet;
    private ExecutorService dispatcherExecutor;
    private RustNativeFFM rustNative;
    private Arena callbackArena;
    private MemorySegment callbackStub;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RustWebServer(RustServerProperties properties, int port, String address,
                         ServletContextInitializer[] initializers) {
        this.properties = properties;
        this.port = port;
        this.address = address;
        this.initializers = initializers;
    }

    @Override
    public void start() throws WebServerException {
        if (running.compareAndSet(false, true)) {
            try {
                log.info("[Rust Server] 启动中...");

                // 1. 初始化 ServletContext
                initServletContext();

                // 2. 加载 Rust 动态库
                loadNativeLibrary();

                // 3. 创建线程池
                initDispatcher();

                // 4. 启动 Rust HTTP 服务器
                startRustServer();

                log.info("[Rust Server] 启动完成，监听 {}:{}", address, port);
            } catch (Exception e) {
                running.set(false);
                throw new WebServerException("Rust Server 启动失败", e);
            }
        }
    }

    private void initServletContext() throws ServletException {
        String contextPath = "";
        servletContext = new RustServletContext(contextPath);

        // 执行所有初始化器
        if (initializers != null) {
            for (ServletContextInitializer initializer : initializers) {
                initializer.onStartup(servletContext);
            }
        }

        // 获取 DispatcherServlet
        Object dispatcherServletBean = servletContext.getAttribute("org.springframework.web.servlet.FrameworkServlet.CONTEXT.dispatcherServlet");
        if (dispatcherServletBean instanceof Servlet servlet) {
            this.dispatcherServlet = servlet;
        }

        log.info("[Rust Server] ServletContext 初始化完成");
    }

    private void loadNativeLibrary() throws IOException {
        Path nativeLibPath = prepareNativeLibrary();
        log.info("[Rust Server] 加载动态库: {}", nativeLibPath);

        try {
            rustNative = new RustNativeFFM(nativeLibPath.toString());
            log.info("[Rust Server] 动态库加载成功");
        } catch (Exception e) {
            throw new IOException("加载 Rust 动态库失败", e);
        }
    }

    private Path prepareNativeLibrary() throws IOException {
        // 从 classpath 中提取对应平台的动态库
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String resourceName;
        String suffix;

        if (os.contains("win")) {
            resourceName = "rust-server/rust_http_server.dll";
            suffix = ".dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            resourceName = arch.contains("aarch64") || arch.contains("arm")
                    ? "rust-server/librust_http_server_arm64.dylib"
                    : "rust-server/librust_http_server.dylib";
            suffix = ".dylib";
        } else {
            resourceName = arch.contains("aarch64") || arch.contains("arm")
                    ? "rust-server/librust_http_server_arm64.so"
                    : "rust-server/librust_http_server.so";
            suffix = ".so";
        }

        ClassPathResource resource = new ClassPathResource(resourceName);
        if (!resource.exists()) {
            throw new FileNotFoundException("Rust 动态库不存在: " + resourceName);
        }

        // 复制到临时目录
        Path tempLib = Files.createTempFile("rust_http_server_", suffix);
        try (InputStream is = resource.getInputStream()) {
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
        }

        return tempLib;
    }

    private void initDispatcher() {
        // 创建线程池
        if (properties.getDispatcher().isUseVirtualThreads()) {
            dispatcherExecutor = Executors.newVirtualThreadPerTaskExecutor();
            log.info("[Rust Server] 使用虚拟线程分发器");
        } else {
            dispatcherExecutor = Executors.newFixedThreadPool(
                    properties.getDispatcher().getMaxPoolSize());
            log.info("[Rust Server] 使用固定线程池分发器, 大小: {}",
                    properties.getDispatcher().getMaxPoolSize());
        }
    }

    private void startRustServer() throws Exception {
        // 创建全局 Arena 用于回调（与服务器生命周期一致）
        callbackArena = Arena.ofShared();
        
        // 创建回调函数
        RustNativeFFM.RequestCallback callback = (requestId, method, uri, headersJson, bodyData, bodyLen) -> {
            // 在线程池中异步处理请求
            dispatcherExecutor.submit(() -> handleRequest(
                    requestId, method, uri, headersJson, bodyData, bodyLen));
            return 0;
        };
        
        // 创建 upcall stub
        callbackStub = RustNativeFFM.createCallback(callback, callbackArena);

        // 调用 Rust 启动服务器
        int result = rustNative.startServer(address, (short) port, callbackStub);
        if (result != 0) {
            throw new Exception("Rust 服务器启动失败，错误码: " + result);
        }

        // 等待服务器启动
        Thread.sleep(500);
        log.info("[Rust Server] Rust HTTP 服务器已启动");
    }

    private void handleRequest(long requestId, String method, String uri,
                                String headersJson, MemorySegment bodyData, long bodyLen) {
        try {
            // 解析请求头
            Map<String, List<String>> headers = objectMapper.readValue(
                    headersJson, new TypeReference<Map<String, List<String>>>() {});

            // 读取请求体
            byte[] body = null;
            if (bodyLen > 0 && bodyData != null && !bodyData.equals(MemorySegment.NULL)) {
                body = bodyData.reinterpret(bodyLen).toArray(ValueLayout.JAVA_BYTE);
            }

            // 构造 Servlet 请求/响应
            RustHttpServletRequest servletRequest = createServletRequest(
                    requestId, method, uri, headers, body);
            RustHttpServletResponse servletResponse = new RustHttpServletResponse(requestId);

            // 调用 Servlet
            if (dispatcherServlet != null) {
                dispatcherServlet.service(servletRequest, servletResponse);
            } else {
                servletResponse.sendError(503, "Service Unavailable");
            }

            // 发送响应到 Rust
            sendResponse(requestId, servletResponse);

        } catch (Exception e) {
            log.error("[Rust Server] 处理请求失败: {}", uri, e);
            sendErrorResponse(requestId, 500);
        }
    }

    private RustHttpServletRequest createServletRequest(
            long requestId, String method, String uri,
            Map<String, List<String>> headers, byte[] body) {
        
        // 构造请求消息（复用现有的 RequestMessage 结构）
        var request = new com.chua.starter.rust.server.ipc.RequestMessage();
        request.setRequestId(requestId);
        request.setMethod(method);
        request.setUri(uri);
        request.setHeaders(headers);
        request.setBody(body);
        request.setRemoteAddr("127.0.0.1"); // TODO: 从 Rust 传递实际地址
        request.setLocalAddr(address + ":" + port);

        return new RustHttpServletRequest(request, servletContext);
    }

    private void sendResponse(long requestId, RustHttpServletResponse response) {
        try (Arena arena = Arena.ofConfined()) {
            var responseMsg = response.toResponseMessage();
            
            // 序列化响应头为 JSON
            String headersJson = objectMapper.writeValueAsString(responseMsg.getHeaders());
            
            // 准备响应体
            MemorySegment bodySegment = MemorySegment.NULL;
            long bodyLen = 0;
            if (responseMsg.getBody() != null && responseMsg.getBody().length > 0) {
                bodyLen = responseMsg.getBody().length;
                bodySegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, responseMsg.getBody());
            }

            // 调用 Rust 发送响应
            int result = rustNative.sendResponse(
                    requestId,
                    (short) responseMsg.getStatus(),
                    headersJson,
                    bodySegment,
                    bodyLen
            );

            if (result != 0) {
                log.error("[Rust Server] 发送响应失败，请求 ID: {}", requestId);
            }
        } catch (Exception e) {
            log.error("[Rust Server] 发送响应异常，请求 ID: {}", requestId, e);
        }
    }

    private void sendErrorResponse(long requestId, int status) {
        try {
            rustNative.sendResponse(requestId, (short) status, "{}", MemorySegment.NULL, 0);
        } catch (Exception e) {
            log.error("[Rust Server] 发送错误响应失败，请求 ID: {}", requestId, e);
        }
    }

    @Override
    public void stop() throws WebServerException {
        if (running.compareAndSet(true, false)) {
            log.info("[Rust Server] 停止中...");

            // 停止 Rust 服务器
            if (rustNative != null) {
                try {
                    rustNative.stopServer();
                } catch (Exception e) {
                    log.error("[Rust Server] 停止 Rust 服务器失败", e);
                }
            }

            // 关闭回调 Arena
            if (callbackArena != null) {
                callbackArena.close();
            }

            // 关闭线程池
            if (dispatcherExecutor != null) {
                dispatcherExecutor.shutdownNow();
            }

            log.info("[Rust Server] 已停止");
        }
    }

    @Override
    public int getPort() {
        return port;
    }
}
