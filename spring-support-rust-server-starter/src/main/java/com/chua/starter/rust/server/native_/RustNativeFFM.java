package com.chua.starter.rust.server.native_;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Java 21 FFM API 接口用于调用 Rust 动态库
 *
 * @author CH
 * @since 4.0.0
 */
public class RustNativeFFM {

    private final SymbolLookup symbolLookup;
    private final Linker linker;
    
    // 函数句柄
    private final MethodHandle rustServerStart;
    private final MethodHandle rustServerStop;
    private final MethodHandle rustServerSendResponse;
    
    // 函数描述符
    private static final FunctionDescriptor START_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,        // 返回值: int
            ValueLayout.ADDRESS,         // host: *const c_char
            ValueLayout.JAVA_SHORT,      // port: u16
            ValueLayout.ADDRESS,         // callback: function pointer
            ValueLayout.ADDRESS          // user_data: *mut c_void
    );
    
    private static final FunctionDescriptor STOP_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT         // 返回值: int
    );
    
    private static final FunctionDescriptor SEND_RESPONSE_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,        // 返回值: int
            ValueLayout.JAVA_LONG,       // request_id: u64
            ValueLayout.JAVA_SHORT,      // status: u16
            ValueLayout.ADDRESS,         // headers_json: *const c_char
            ValueLayout.ADDRESS,         // body_data: *const u8
            ValueLayout.JAVA_LONG        // body_len: usize
    );
    
    /**
     * 回调函数描述符
     */
    public static final FunctionDescriptor CALLBACK_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,        // 返回值: int
            ValueLayout.JAVA_LONG,       // request_id: u64
            ValueLayout.ADDRESS,         // method: *const c_char
            ValueLayout.ADDRESS,         // uri: *const c_char
            ValueLayout.ADDRESS,         // headers_json: *const c_char
            ValueLayout.ADDRESS,         // body_data: *const u8
            ValueLayout.JAVA_LONG,       // body_len: usize
            ValueLayout.ADDRESS          // user_data: *mut c_void
    );
    
    public RustNativeFFM(String libraryPath) {
        System.load(libraryPath);
        this.symbolLookup = SymbolLookup.loaderLookup();
        this.linker = Linker.nativeLinker();
        
        try {
            // 查找函数符号
            MemorySegment startSymbol = symbolLookup.find("rust_server_start")
                    .orElseThrow(() -> new UnsatisfiedLinkError("rust_server_start not found"));
            MemorySegment stopSymbol = symbolLookup.find("rust_server_stop")
                    .orElseThrow(() -> new UnsatisfiedLinkError("rust_server_stop not found"));
            MemorySegment sendResponseSymbol = symbolLookup.find("rust_server_send_response")
                    .orElseThrow(() -> new UnsatisfiedLinkError("rust_server_send_response not found"));
            
            // 获取函数句柄
            this.rustServerStart = linker.downcallHandle(startSymbol, START_DESCRIPTOR);
            this.rustServerStop = linker.downcallHandle(stopSymbol, STOP_DESCRIPTOR);
            this.rustServerSendResponse = linker.downcallHandle(sendResponseSymbol, SEND_RESPONSE_DESCRIPTOR);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Rust native library", e);
        }
    }
    
    /**
     * 启动 HTTP 服务器
     *
     * @param host     监听地址
     * @param port     监听端口
     * @param callback 请求回调函数
     * @return 0 表示成功，-1 表示失败
     */
    public int startServer(String host, short port, MemorySegment callback) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hostSegment = arena.allocateUtf8String(host);
            return (int) rustServerStart.invoke(hostSegment, port, callback, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to start server", e);
        }
    }

    /**
     * 停止 HTTP 服务器
     *
     * @return 0 表示成功，-1 表示失败
     */
    public int stopServer() {
        try {
            return (int) rustServerStop.invoke();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to stop server", e);
        }
    }
    
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
    public int sendResponse(long requestId, short status, String headersJson, 
                           MemorySegment bodyData, long bodyLen) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment headersSegment = headersJson != null 
                    ? arena.allocateUtf8String(headersJson) 
                    : MemorySegment.NULL;
            
            return (int) rustServerSendResponse.invoke(
                    requestId, status, headersSegment, bodyData, bodyLen);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to send response", e);
        }
    }

    /**
     * 创建回调函数的 upcall stub
     *
     * @param callback Java 回调接口实现
     * @param arena    内存管理 Arena
     * @return 回调函数的内存段
     */
    public static MemorySegment createCallback(RequestCallback callback, Arena arena) {
        try {
            MethodHandle handle = MethodHandles.lookup().findStatic(
                RustNativeFFM.class, "callbackTrampoline",
                MethodType.methodType(int.class, long.class, MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, long.class, MemorySegment.class, RequestCallback.class)
            );
            handle = MethodHandles.insertArguments(handle, 7, callback);
            return linker().upcallStub(handle, CALLBACK_DESCRIPTOR, arena);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create callback", e);
        }
    }
    
    private static int callbackTrampoline(long requestId, MemorySegment methodSeg, MemorySegment uriSeg,
                                         MemorySegment headersSeg, MemorySegment bodySeg, long bodyLen,
                                         MemorySegment userDataSeg, RequestCallback callback) {
        try {
            String method = methodSeg.reinterpret(1000).getUtf8String(0);
            String uri = uriSeg.reinterpret(1000).getUtf8String(0);
            String headers = headersSeg.reinterpret(10000).getUtf8String(0);
            
            return callback.onRequest(requestId, method, uri, headers, bodySeg, bodyLen);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static Linker linker() {
        return Linker.nativeLinker();
    }
    
    /**
     * 回调接口
     */
    @FunctionalInterface
    public interface RequestCallback {
        int onRequest(long requestId, String method, String uri, String headersJson,
                     MemorySegment bodyData, long bodyLen);
    }
}
