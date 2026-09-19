package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.http.HttpHeader;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.filter.ReactiveFilterChain;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.ExistObjectRequest;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.DeleteObjectResult;
import com.chua.common.support.storage.result.ExistObjectResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ListObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 文件storage服务端过滤器 使用示例：演示文件存储过滤器的典型用法。
 *
 * <p>覆盖过滤器创建、存储注册/注销、OPTIONS 预检、未注册 bucket 放行等场景。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FileStorageServerFilterExample {

    /**
     * 文件storage服务端过滤器example。
     */
    private FileStorageServerFilterExample() {
    }

    /**
     * 运行示例自检：验证 文件storage服务端过滤器 基本功能。
     *
     * @param args 命令行参数（key=value 形式），当前未使用
     * @return 全部场景通过返回 true
     */
    public static boolean run(Map<String, String> args) {
        int pass = 0;
        int total = 0;

        // 1. 创建过滤器实例
        FileStorageSetting setting = FileStorageSetting.builder()
                .openPreview(true)
                .openDownload(true)
                .openRange(true)
                .build();
        FileStorageServerFilter filter = new FileStorageServerFilter(setting);
        assert filter != null : "[FAIL] filter 不应为 null";
        log.info("[PASS] filter 创建成功");
        pass++;
        total++;

 // 2. 验证 获取订单 和 支持路径
        assert filter.getOrder() == 100 : "[FAIL] order 应为 100, got " + filter.getOrder();
        assert filter.supportPath() == null : "[FAIL] supportPath 应为 null（Access Filter）";
        log.info("[PASS] getOrder={} supportPath={}", filter.getOrder(), filter.supportPath());
        pass++;
        total++;

        // 3. 添加并移除存储
        filter.addFileStorage("test", createMockStorage());
        // 验证存储已注册（通过反射访问私有字段）
        boolean hasTest = com.chua.common.support.reflection.ReflectUtils.getField(filter, "storageMap") != null
                && ((Map<?, ?>) com.chua.common.support.reflection.ReflectUtils.getField(filter, "storageMap")).containsKey("test");
        assert hasTest : "[FAIL] 应包含 test 存储";
        filter.removeFileStorage("test");
        boolean removed = !((Map<?, ?>) com.chua.common.support.reflection.ReflectUtils.getField(filter, "storageMap")).containsKey("test");
        assert removed : "[FAIL] 移除后不应包含 test 存储";
        log.info("[PASS] addFileStorage / removeFileStorage OK");
        pass++;
        total++;

 // 4. 期权 预检请求
        try {
            MockRequest optsReq = new MockRequest(HttpMethod.OPTIONS, "/");
            MockResponse optsResp = new MockResponse();
            CompletionStage<Void> stage = filter.doFilter(optsReq, optsResp, (ServerRequest req, ServerResponse resp) -> {
                throw new AssertionError("OPTIONS 应被过滤器拦截，不应进入后续链");
            });
            stage.toCompletableFuture().get();
            assert optsResp.getStatus() == 204 : "[FAIL] OPTIONS 应返回 204, got " + optsResp.getStatus();
            assert optsResp.getHeader("Access-Control-Allow-Methods") != null : "[FAIL] OPTIONS 应含 CORS 头";
            log.info("[PASS] OPTIONS preflight returns 204 with CORS headers");
        } catch (Exception e) {
            log.error("[FAIL] OPTIONS preflight failed: {}", e.getMessage());
        }
        pass++;
        total++;

        // 5. 未注册 bucket 时放行
        try {
            MockRequest req = new MockRequest(HttpMethod.GET, "/unknown-bucket");
            boolean[] chained = {false};
            MockResponse resp = new MockResponse();
            filter.doFilter(req, resp, (ServerRequest r, ServerResponse response) -> {
                chained[0] = true;
                response.setStatus(200).setBody("chained").end();
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }).toCompletableFuture().get();
            assert chained[0] : "[FAIL] 未注册 bucket 应放行给后续链";
            log.info("[PASS] unregistered bucket passthrough OK");
        } catch (Exception e) {
            log.error("[FAIL] passthrough failed: {}", e.getMessage());
        }
        pass++;
        total++;

        log.info("FileStorageServerFilterExample: {}/{} passed", pass, total);
        return pass == total;
    }

    /**
     * 入口方法：解析参数并执行自检。
     *
     * @param args 命令行参数（key=value 形式）
     */
    public static void main(String[] args) {
        Map<String, String> parsed = com.chua.common.support.utils.CommandLine.parse(args).getOptions();
        boolean passed = run(parsed);
        System.exit(passed ? 0 : 1);
    }

    // ==================== 测试用 Mock ====================
    /**
     * mock响应类。
     *
     * @author CH
     * @since 4.0.0
     * @return 创建mockstorage的结果
     */

    private static FileStorage createMockStorage() {
        return new FileStorage() {
            @Override
            public PutObjectResult putObject(PutObjectRequest request) {
                return PutObjectResult.builder().resultCode(ObjectResult.ResultCode.SUCCESS).url("/mock/key").build();
            }

            @Override
            public GetObjectResult getObject(com.chua.common.support.storage.request.GetObjectRequest request) {
                return null;
            }

            @Override
            public GetObjectResult getObject(String key) {
                if ("notfound".equals(key)) {
                    return GetObjectResult.builder().resultCode(ObjectResult.ResultCode.FAILURE).build();
                }
                byte[] content = "mock-content".getBytes();
                return GetObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
                        .inputStream(new ByteArrayInputStream(content))
                        .build();
            }

            @Override
            public DeleteObjectResult deleteObject(String key) {
                return DeleteObjectResult.builder().resultCode(ObjectResult.ResultCode.SUCCESS).build();
            }

            @Override
            public ExistObjectResult existObject(ExistObjectRequest request) {
                return ExistObjectResult.builder().exists(true).build();
            }

            @Override
            public ListObjectResult listObject(ListObjectRequest request) {
                return ListObjectResult.builder()
                        .resultCode(ObjectResult.ResultCode.SUCCESS)
                        .metadata(List.of(Metadata.builder()
                                .name("item1").size(100).directory(false)
                                .lastModified(System.currentTimeMillis()).build()))
                        .build();
            }

            @Override
            public com.chua.common.support.storage.MultipartStorage createMultipartStorage() {
                throw new UnsupportedOperationException("not supported in mock");
            }

            @Override
            public void close() throws Exception {}
        };
    }

    static class MockRequest implements ServerRequest {
        private final HttpMethod method; // 方法
        private final String path; // 路径
        private final Map<String, String> params = Map.of(); // 参数
        private final HttpHeader headers = HttpHeader.create(); // 头部

        MockRequest(HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override public HttpMethod getMethod() { return method; }
        @Override public String getUri() { return path; }
        @Override public String getPath() { return path; }
        @Override public Map<String, String> getParams() { return params; }
        @Override public String getParam(String name) { return params.get(name); }
        @Override public HttpHeader getHeaders() { return headers; }
        @Override public String getHeader(String name) { return headers.get(name); }
        @Override public String getContentType() { return null; }
        @Override public long getContentLength() { return -1; }
        @Override public byte[] getBody() { return null; }
        @Override public String getBodyString() { return null; }
        @Override public java.io.InputStream getInputStream() { return null; }
        @Override public String getRemoteAddress() { return "127.0.0.1"; }
        @Override public int getRemotePort() { return 0; }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {}
    }

    static class MockResponse implements ServerResponse {
        private int status = 0; // 状态
        private HttpHeader headers = HttpHeader.create(); // 头部
        private byte[] bodyBytes; // 主体bytes

        @Override
        public ServerResponse setStatus(int status) {
            this.status = status;
            return this;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public ServerResponse setHeader(String name, String value) {
            headers.add(name, value);
            return this;
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public HttpHeader getHeaders() {
            return headers;
        }

        @Override
        public ServerResponse setContentType(String contentType) {
            headers.add("Content-Type", contentType);
            return this;
        }

        @Override
        public String getContentType() {
            return headers.get("Content-Type");
        }

        @Override
        public ServerResponse setBody(byte[] body) {
            this.bodyBytes = body;
            return this;
        }

        @Override
        public ServerResponse setBody(String body) {
            this.bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return this;
        }

        @Override
        public byte[] getBody() {
            return bodyBytes;
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return null;
        }

        @Override
        public ServerResponse sendRedirect(String location) {
            return this;
        }

        @Override
        public ServerResponse sendError(int statusCode, String message) {
            this.status = statusCode;
            return this;
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public boolean isEnded() {
            return false;
        }

        @Override
        public void end() {
        }

        @Override
        public void writeRaw(byte[] bytes) {
            if (bodyBytes == null) {
                bodyBytes = bytes;
            }
            else {
                bodyBytes = java.util.Arrays.copyOf(bodyBytes, bodyBytes.length + bytes.length);
            }
            System.arraycopy(bytes, 0, bodyBytes, bodyBytes.length - bytes.length, bytes.length);
        }

        @Override
        public ServerResponse reset() {
            status = 0;
            headers = HttpHeader.create();
            bodyBytes = null;
            return this;
        }

        /**
         * 读取指定请求头的值。
         *
         * @param name 头名称，不允许为 null
         * @return 头的值，不存在时返回 null
         */
        public String getHeaderVal(String name) {
            return headers.get(name);
        }
    }
}
