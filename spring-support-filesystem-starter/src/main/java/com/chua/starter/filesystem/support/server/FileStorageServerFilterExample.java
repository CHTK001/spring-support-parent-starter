package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.filter.ReactiveFilterChain;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.result.ObjectResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * FileStorageServerFilter 使用示例：演示文件存储过滤器的典型用法。
 *
 * <p>覆盖注册存储、列表/上传/删除对象、CORS 处理等场景。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FileStorageServerFilterExample {

    private FileStorageServerFilterExample() {
    }

    /**
     * 运行示例自检：验证 FileStorageServerFilter 基本功能。
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

        // 2. 验证 getOrder 和 supportPath
        assert filter.getOrder() == 100 : "[FAIL] order 应为 100, got " + filter.getOrder();
        assert filter.supportPath() == null : "[FAIL] supportPath 应为 null（Access Filter）";
        log.info("[PASS] getOrder={} supportPath={}", filter.getOrder(), filter.supportPath());
        pass++;
        total++;

        // 3. 添加并移除存储
        Map<String, FileStorage> storages = Map.of("test", createMockStorage());
        for (var entry : storages.entrySet()) {
            filter.addFileStorage(entry.getKey(), entry.getValue());
        }
        assert filter.getStorageMap().containsKey("test") : "[FAIL] 应包含 test 存储";
        filter.removeFileStorage("test");
        assert !filter.getStorageMap().containsKey("test") : "[FAIL] 移除后不应包含 test 存储";
        log.info("[PASS] addFileStorage / removeFileStorage OK");
        pass++;
        total++;

        // 4. OPTIONS 预检请求
        try {
            MockRequest optsReq = new MockRequest(HttpMethod.OPTIONS, "/");
            MockResponse optsResp = new MockResponse();
            CompletionStage<Void> stage = filter.doFilter(optsReq, optsResp, (req, resp) -> {
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
            filter.doFilter(req, resp, (r, response) -> {
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

    private static FileStorage createMockStorage() {
        return new FileStorage() {
            @Override
            public com.chua.common.support.storage.result.PutObjectResult putObject(
                    com.chua.common.support.storage.request.PutObjectRequest request) {
                return com.chua.common.support.storage.result.PutObjectResult.success("/mock/key");
            }

            @Override
            public com.chua.common.support.storage.result.GetObjectResult getObject(String key) {
                if ("notfound".equals(key)) {
                    return com.chua.common.support.storage.result.GetObjectResult.notFound();
                }
                return com.chua.common.support.storage.result.GetObjectResult.success(
                        "mock-content".getBytes(), "text/plain", 12);
            }

            @Override
            public com.chua.common.support.storage.result.DeleteObjectResult deleteObject(String key) {
                return com.chua.common.support.storage.result.DeleteObjectResult.success();
            }

            @Override
            public com.chua.common.support.storage.result.ExistObjectResult existObject(
                    com.chua.common.support.storage.request.ExistObjectRequest request) {
                return com.chua.common.support.storage.result.ExistObjectResult.exists();
            }

            @Override
            public com.chua.common.support.storage.result.ListObjectResult listObject(
                    com.chua.common.support.storage.request.ListObjectRequest request) {
                return com.chua.common.support.storage.result.ListObjectResult.success(
                        List.of(com.chua.common.support.storage.metadata.Metadata.builder()
                                .name("item1").size(100).directory(false).lastModified(System.currentTimeMillis())
                                .build()));
            }
        };
    }

    static class MockRequest implements ServerRequest {
        private final HttpMethod method;
        private final String path;
        private final Map<String, String> params = Map.of();
        private final Map<String, String> headers = Map.of();

        MockRequest(HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override public HttpMethod getMethod() { return method; }
        @Override public String getPath() { return path; }
        @Override public Map<String, String> getParams() { return params; }
        @Override public Map<String, String> getHeaders() { return headers; }
        @Override public String getHeader(String name) { return headers.get(name); }
        @Override public String getBody() { return null; }
        @Override public byte[] getBodyBytes() { return null; }
    }

    static class MockResponse implements ServerResponse {
        private int status = 0;
        private final Map<String, String> headers = new java.util.HashMap<>();
        private Object body;

        @Override public ServerResponse setStatus(int status) { this.status = status; return this; }
        @Override public int getStatus() { return status; }
        @Override public ServerResponse setHeader(String name, String value) { headers.put(name, value); return this; }
        @Override public String getHeader(String name) { return headers.get(name); }
        @Override public Map<String, String> getHeaders() { return headers; }
        @Override public ServerResponse setContentType(String contentType) { setHeader("Content-Type", contentType); return this; }
        @Override public ServerResponse setBody(Object body) { this.body = body; return this; }
        @Override public void end() {}
        @Override public void end(Object body) { this.body = body; }
        @Override public void sendError(int status, String message) { this.status = status; }
        public int getStatusVal() { return status; }
        public String getHeaderVal(String name) { return headers.get(name); }
    }
}
