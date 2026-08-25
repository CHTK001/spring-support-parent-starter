package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.starter.filesystem.support.local.LocalFileStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileStorageServerFilter} 全功能集成测试。
 *
 * <p>启动真实 HTTP 服务器（JDK HttpServer + FileStorageServerFilter + LocalFileStorage），
 * 通过 HttpClient 端到端验证上传、下载、预览、附件、Range 分段、存在性检查、
 * 列表、删除、水印头与路径放行等全部能力。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileStorageServerFilterIntegrationTest {

    /**
     * 被测服务器
     */
    private Server server;

    /**
     * 服务端口
     */
    private int port;

    /**
     * HTTP 客户端
     */
    private HttpClient client;

    /**
     * 存储桶名称
     */
    private static final String BUCKET = "local";

    /**
     * 启动服务器并初始化客户端。
     *
     * @throws Exception 启动失败时抛出
     */
    @BeforeAll
    void startServer() throws Exception {
        // 探测空闲端口，避免测试间冲突
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        FileStorageSetting setting = FileStorageSetting.builder()
                .openPreview(true)
                .openDownload(true)
                .openRange(true)
                .openWatermark(true)
                .watermark("CONFIDENTIAL")
                .openRemoteFile(false)
                .build();

        FileStorageServerFilter filter = new FileStorageServerFilter(setting);
        FileStorage storage = new LocalFileStorage(BucketSetting.builder().bucket("itest").build());
        filter.addFileStorage(BUCKET, storage);

        server = ServerBuilder.create()
                .type("jdk")
                .host("127.0.0.1")
                .port(port)
                .build();
        server.addFilter(filter);
        server.start();

        client = HttpClient.newHttpClient();
        waitUntilReady();
    }

    /**
     * 关闭服务器。
     */
    @AfterAll
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * 等待服务就绪。
     *
     * @throws Exception 探测失败时抛出
     */
    private void waitUntilReady() throws Exception {
        for (int i = 0; i < 50; i++) {
            try {
                HttpResponse<byte[]> resp = client.send(
                        HttpRequest.newBuilder().uri(baseUri("/")).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() > 0) {
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(100);
        }
        assertTrue(false, "服务器未能在预期时间内就绪");
    }

    @Test
    @DisplayName("PUT 上传后 GET 下载内容应一致且为内联预览")
    void shouldPutThenGet() throws Exception {
        byte[] data = "hello filestorage server".getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> put = send("PUT", "/local/hello.txt", data);
        assertEquals(200, put.statusCode(), "上传应返回 200");
        assertTrue(put.body().contains("\"status\":\"SUCCESS\""), "上传响应应标记成功");
        assertTrue(put.body().contains("hello.txt"), "上传响应应包含对象 Key");

        HttpResponse<byte[]> get = sendBytes("GET", "/local/hello.txt", null);
        assertEquals(200, get.statusCode(), "下载应返回 200");
        assertArrayEquals(data, get.body(), "下载内容应与上传内容一致");
        assertTrue(get.headers().firstValue("Content-Disposition").orElse("")
                        .startsWith("inline"),
                "预览模式应为内联 Content-Disposition");
        assertTrue(get.headers().firstValue("Content-Type").orElse("").contains("text/plain"),
                "txt 文件应推断为 text/plain");
        assertEquals("CONFIDENTIAL",
                get.headers().firstValue("X-Watermark").orElse(""),
                "开启水印时应携带水印响应头");
    }

    @Test
    @DisplayName("download=true 参数应触发附件下载")
    void shouldDownloadAsAttachment() throws Exception {
        send("PUT", "/local/attach.txt", "attach".getBytes(StandardCharsets.UTF_8));

        HttpResponse<byte[]> get = sendBytes("GET", "/local/attach.txt?download=true", null);
        assertEquals(200, get.statusCode());
        assertTrue(get.headers().firstValue("Content-Disposition").orElse("")
                        .startsWith("attachment"),
                "download=true 应返回附件 Content-Disposition");
    }

    @Test
    @DisplayName("Range 请求应返回 206 与正确分片")
    void shouldSupportRangeRequest() throws Exception {
        byte[] data = "0123456789".getBytes(StandardCharsets.UTF_8);
        send("PUT", "/local/range.bin", data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(baseUri("/local/range.bin"))
                .header("Range", "bytes=2-5")
                .GET()
                .build();
        HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(206, resp.statusCode(), "分段下载应返回 206");
        assertArrayEquals("2345".getBytes(StandardCharsets.UTF_8), resp.body(),
                "分片内容应为第 2 至第 5 字节");
        assertEquals("bytes 2-5/10",
                resp.headers().firstValue("Content-Range").orElse(""),
                "Content-Range 应描述完整区间");
    }

    @Test
    @DisplayName("HEAD 请求应正确反映对象存在性")
    void shouldCheckExistenceViaHead() throws Exception {
        send("PUT", "/local/head.txt", "head".getBytes(StandardCharsets.UTF_8));

        HttpResponse<Void> exists = client.send(
                HttpRequest.newBuilder().uri(baseUri("/local/head.txt"))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, exists.statusCode(), "存在的对象 HEAD 应返回 200");

        HttpResponse<Void> missing = client.send(
                HttpRequest.newBuilder().uri(baseUri("/local/no-such.txt"))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(404, missing.statusCode(), "不存在的对象 HEAD 应返回 404");
    }

    @Test
    @DisplayName("GET 存储桶根路径应返回对象列表 JSON")
    void shouldListObjects() throws Exception {
        send("PUT", "/local/list-a.txt", "a".getBytes(StandardCharsets.UTF_8));
        send("PUT", "/local/list-b.txt", "b".getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> list = send("GET", "/local", null);
        assertEquals(200, list.statusCode(), "列表应返回 200");
        assertTrue(list.body().contains("\"bucket\":\"" + BUCKET + "\""), "列表应包含存储桶名");
        assertTrue(list.body().contains("list-a.txt"), "列表应包含已上传文件 a");
        assertTrue(list.body().contains("list-b.txt"), "列表应包含已上传文件 b");
        assertTrue(list.headers().firstValue("Content-Type").orElse("").contains("application/json"),
                "列表响应应为 JSON");
    }

    @Test
    @DisplayName("支持子目录 Key 的上传与下载")
    void shouldSupportNestedKey() throws Exception {
        byte[] data = "nested".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> put = send("PUT", "/local/docs/2026/nested.txt", data);
        assertEquals(200, put.statusCode(), "子目录上传应成功");

        HttpResponse<byte[]> get = sendBytes("GET", "/local/docs/2026/nested.txt", null);
        assertEquals(200, get.statusCode(), "子目录下载应成功");
        assertArrayEquals(data, get.body());
    }

    @Test
    @DisplayName("GET 目录路径应回退返回该目录的对象列表")
    void shouldListSubDirectoryByPath() throws Exception {
        send("PUT", "/local/nav/sub/deep.txt", "deep".getBytes(StandardCharsets.UTF_8));

        // 一级目录
        HttpResponse<String> level1 = send("GET", "/local/nav", null);
        assertEquals(200, level1.statusCode(), "目录路径应返回 200 列表");
        assertTrue(level1.body().contains("\"path\":\"nav\""), "列表应携带目录路径");
        assertTrue(level1.body().contains("\"name\":\"sub\""), "一级目录应包含 sub");
        assertTrue(level1.body().contains("\"directory\":true"), "sub 应标记为目录");

        // 二级目录
        HttpResponse<String> level2 = send("GET", "/local/nav/sub", null);
        assertEquals(200, level2.statusCode());
        assertTrue(level2.body().contains("deep.txt"), "二级目录应包含 deep.txt");
        assertTrue(level2.body().contains("\"lastModified\":"),
                "列表项应携带最后修改时间");

        // 尾斜杠显式目录访问
        HttpResponse<String> trailing = send("GET", "/local/nav/", null);
        assertEquals(200, trailing.statusCode(), "尾斜杠目录访问应返回 200");
        assertTrue(trailing.body().contains("\"path\":\"nav\""), "尾斜杠列表应指向 nav 目录");
    }

    @Test
    @DisplayName("DELETE 后对象应不可再获取")
    void shouldDeleteObject() throws Exception {
        send("PUT", "/local/gone.txt", "delete me".getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> del = send("DELETE", "/local/gone.txt", null);
        assertEquals(200, del.statusCode(), "删除应返回 200");
        assertTrue(del.body().contains("\"status\":\"SUCCESS\""), "删除响应应标记成功");

        HttpResponse<byte[]> get = sendBytes("GET", "/local/gone.txt", null);
        assertEquals(404, get.statusCode(), "删除后获取应返回 404");
    }

    @Test
    @DisplayName("空 body 上传应被拒绝")
    void shouldRejectEmptyUpload() throws Exception {
        HttpResponse<String> put = send("PUT", "/local/empty.txt", new byte[0]);
        assertEquals(400, put.statusCode(), "空 body 上传应返回 400");
    }

    @Test
    @DisplayName("未注册的存储路径应放行给后续链路处理")
    void shouldPassthroughUnknownBucket() throws Exception {
        HttpResponse<byte[]> resp = sendBytes("GET", "/unknown-bucket/file.txt", null);
        // 放行后无路由匹配，由框架默认处理器返回 404，而非过滤器的业务语义
        assertEquals(404, resp.statusCode());
        assertFalse(resp.headers().firstValue("X-Watermark").isPresent(),
                "放行路径不应附加文件存储过滤器的水印头");
    }

    @Test
    @DisplayName("远程文件透传关闭时应拒绝 http(s) Key")
    void shouldRejectRemoteFileWhenDisabled() throws Exception {
        HttpResponse<byte[]> resp = sendBytes("GET",
                "/" + BUCKET + "/http://evil.example.com/payload", null);
        assertEquals(400, resp.statusCode(), "含冒号的非法 Key 应被直接拒绝");
    }

    /**
     * 构建请求 URI。
     *
     * @param path 相对路径
     * @return URI
     */
    private URI baseUri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    /**
     * 发送带字节数组体的请求。
     *
     * @param method HTTP 方法
     * @param path   路径
     * @param body   请求体
     * @return 字符串响应
     * @throws Exception 请求失败时抛出
     */
    private HttpResponse<String> send(String method, String path, byte[] body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(baseUri(path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 发送 GET 请求并获取字节数组响应。
     *
     * @param method HTTP 方法
     * @param path   路径
     * @param body   请求体
     * @return 字节数组响应
     * @throws Exception 请求失败时抛出
     */
    private HttpResponse<byte[]> sendBytes(String method, String path, byte[] body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(baseUri(path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
