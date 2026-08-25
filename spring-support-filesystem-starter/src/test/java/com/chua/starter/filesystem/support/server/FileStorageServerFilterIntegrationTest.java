package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.starter.filesystem.support.local.LocalFileStorage;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileStorageServerFilter} 全功能集成测试。
 *
 * <p>启动三套真实 HTTP 服务进行端到端验证：</p>
 * <ul>
 *   <li>主服务器 — 全能力开启（预览/下载/分段/水印/远程透传关闭），覆盖 CRUD、目录浏览、Range、安全防护与边界场景</li>
 *   <li>严格服务器 — 全能力关闭，验证访问模式收敛与开关降级行为</li>
 *   <li>远程源服务器 — 内置 JDK HttpServer 提供固定字节，配合 openRemoteFile=true 验证远程透传</li>
 * </ul>
 *
 * <p>用例相互独立（各自准备与清理数据），可任意顺序重复执行。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileStorageServerFilterIntegrationTest {

    /** 主服务器 */
    private Server server;

    /** 严格模式服务器（全部开关关闭） */
    private Server strictServer;

    /** 远程文件源服务器 */
    private com.sun.net.httpserver.HttpServer remoteSource;

    /** 主服务器端口 */
    private int port;

    /** 严格服务器端口 */
    private int strictPort;

    /** 远程源服务器端口 */
    private int remotePort;

    /** HTTP 客户端 */
    private HttpClient client;

    /** 存储桶名称 */
    private static final String BUCKET = "local";

    /** 远程源返回的固定内容 */
    private static final byte[] REMOTE_BYTES = "remote-payload-content".getBytes(StandardCharsets.UTF_8);

    /** 简易 HTTP 客户端封装 */
    private HttpClient http;

    /**
     * 启动全部服务器并初始化客户端。
     *
     * @throws Exception 启动失败时抛出
     */
    @BeforeAll
    void startServers() throws Exception {
        port = freePort();
        strictPort = freePort();
        remotePort = freePort();

        // 主服务器：默认能力全开（水印 CONFIDENTIAL、远程透传关闭）
        FileStorageServerFilter filter = new FilterBuilder()
                .bucket(BUCKET).watermark("CONFIDENTIAL").build();
        server = newServer(port, filter);
        server.start();

        // 严格服务器：预览/下载/分段/水印全部关闭
        FileStorageSetting strictSetting = FileStorageSetting.builder()
                .openPreview(false)
                .openDownload(false)
                .openRange(false)
                .openWatermark(false)
                .build();
        FileStorageServerFilter strictFilter = new FileStorageServerFilter(strictSetting);
        strictFilter.addFileStorage(BUCKET, new LocalFileStorage(BucketSetting.builder().bucket("strict").build()));
        strictServer = newServer(strictPort, strictFilter);
        strictServer.start();

        // 远程源服务器：GET /payload.bin 返回固定字节
        remoteSource = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", remotePort), 0);
        remoteSource.createContext("/payload.bin", (HttpExchange exchange) -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, REMOTE_BYTES.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(REMOTE_BYTES);
            } finally {
                exchange.close();
            }
        });
        remoteSource.start();

        client = new HttpClient();
        waitUntilReady();
    }

    /**
     * 关闭全部服务器。
     */
    @AfterAll
    void stopServers() {
        if (server != null) {
            server.stop();
        }
        if (strictServer != null) {
            strictServer.stop();
        }
        if (remoteSource != null) {
            remoteSource.stop(0);
        }
    }

    /**
     * 探测空闲端口。
     *
     * @return 可用端口号
     * @throws Exception 探测失败时抛出
     */
    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    /**
     * 构建 JDK HTTP 服务器并挂载过滤器。
     *
     * @param portNumber 监听端口
     * @param filter     文件存储过滤器
     * @return 已构建的服务器
     */
    private static Server newServer(int portNumber, FileStorageServerFilter filter) {
        Server built = ServerBuilder.create()
                .type("jdk")
                .host("127.0.0.1")
                .port(portNumber)
                .build();
        built.addFilter(filter);
        return built;
    }

    /**
     * 过滤器构建辅助：注册 local 存储。
     */
    private class FilterBuilder {

        /**
         * 存储桶内部目录名
         */
        private String bucketDir = "itest";

        /**
         * 水印文本
         */
        private String watermark;

        /**
         * 是否允许远程文件透传
         */
        private boolean openRemoteFile = false;

        /**
         * 设置存储桶内部目录名
         *
         * @param value 目录名
         * @return this
         */
        FilterBuilder bucket(String value) {
            this.bucketDir = value;
            return this;
        }

        /**
         * 设置水印文本
         *
         * @param value 水印
         * @return this
         */
        FilterBuilder watermark(String value) {
            this.watermark = value;
            return this;
        }

        /**
         * 设置远程透传开关
         *
         * @param value 开关
         * @return this
         */
        FilterBuilder remote(boolean value) {
            this.openRemoteFile = value;
            return this;
        }

        /**
         * 构建过滤器实例
         *
         * @return 过滤器
         */
        FileStorageServerFilter build() {
            FileStorageSetting setting = FileStorageSetting.builder()
                    .openPreview(true)
                    .openDownload(true)
                    .openRange(true)
                    .openWatermark(watermark != null)
                    .watermark(watermark)
                    .openRemoteFile(openRemoteFile)
                    .build();
            FileStorageServerFilter created = new FileStorageServerFilter(setting);
            FileStorage storage = new LocalFileStorage(BucketSetting.builder().bucket(bucketDir).build());
            created.addFileStorage(BUCKET, storage);
            return created;
        }
    }

    /**
     * 等待主服务就绪。
     *
     * @throws Exception 探测失败时抛出
     */
    private void waitUntilReady() throws Exception {
        for (int i = 0; i < 50; i++) {
            HttpResponse resp = client.send("GET", "/local/", null);
            if (resp.status > 0 && resp.status != 500) {
                return;
            }
            Thread.sleep(100);
        }
        assertTrue(false, "服务器未能在预期时间内就绪");
    }

    /* ==================== 请求封装 ==================== */

    /**
     * HTTP 响应封装
     */
    static class HttpResponse {
        /**
         * 状态码
         */
        int status;

        /**
         * 响应头（小写键）
         */
        java.util.Map<String, String> headers;

        /**
         * 响应体
         */
        byte[] body;
    }

    /**
     * 简易 HTTP 客户端。
     */
    class HttpClient {

        /**
         * 底层客户端
         */
        private final java.net.http.HttpClient delegate = java.net.http.HttpClient.newHttpClient();

        /**
         * 发送请求。
         *
         * @param method HTTP 方法
         * @param path   路径（含查询串）
         * @param body   请求体（可为 null）
         * @return 响应
         * @throws Exception 请求失败时抛出
         */
        HttpResponse send(String method, String path, byte[] body) throws Exception {
            return sendTo(port, method, path, body, java.util.Map.of());
        }

        /**
         * 发送请求（自定义头）。
         *
         * @param method  HTTP 方法
         * @param path    路径
         * @param body    请求体
         * @param headers 附加请求头
         * @return 响应
         * @throws Exception 请求失败时抛出
         */
        HttpResponse send(String method, String path, byte[] body, java.util.Map<String, String> headers) throws Exception {
            return sendTo(port, method, path, body, headers);
        }

        /**
         * 向指定服务器发送请求。
         *
         * @param targetPort 目标端口
         * @param method     HTTP 方法
         * @param path       路径
         * @param body       请求体
         * @param headers    附加请求头
         * @return 响应
         * @throws Exception 请求失败时抛出
         */
        HttpResponse sendTo(int targetPort, String method, String path, byte[] body, java.util.Map<String, String> headers) throws Exception {
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + targetPort + path));
            headers.forEach(builder::header);
            if ("PATCH".equals(method)) {
                builder.method(method, body == null
                        ? java.net.http.HttpRequest.BodyPublishers.noBody()
                        : java.net.http.HttpRequest.BodyPublishers.ofByteArray(body));
            } else if (body == null) {
                builder.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, java.net.http.HttpRequest.BodyPublishers.ofByteArray(body));
            }
            java.net.http.HttpResponse<byte[]> resp =
                    delegate.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse result = new HttpResponse();
            result.status = resp.statusCode();
            result.body = resp.body();
            result.headers = new java.util.HashMap<>();
            resp.headers().map().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    result.headers.put(name.toLowerCase(), String.join(", ", values));
                }
            });
            return result;
        }
    }

    /**
     * 编码 URL 路径段。
     *
     * @param segment 原始片段
     * @return 编码后的片段
     */
    private static String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8);
    }

    /**
     * 拼接主服务器对象路径。
     *
     * @param key 对象 Key
     * @return 完整路径
     */
    private static String objPath(String key) {
        StringBuilder sb = new StringBuilder("/").append(BUCKET);
        for (String part : key.split("/")) {
            sb.append("/").append(enc(part));
        }
        return sb.toString();
    }

    /* ==================== 一、基础 CRUD ==================== */

    @Test
    @DisplayName("CRUD-01 上传后下载内容一致")
    void crud01_putThenGet() throws Exception {
        byte[] data = "crud01-hello".getBytes(StandardCharsets.UTF_8);
        assertEquals(200, client.send("PUT", objPath("c01.txt"), data).status);
        HttpResponse get = client.send("GET", objPath("c01.txt"), null);
        assertEquals(200, get.status);
        assertArrayEquals(data, get.body);
    }

    @Test
    @DisplayName("CRUD-02 上传响应包含 SUCCESS 与 Key 回显")
    void crud02_putResponseBody() throws Exception {
        HttpResponse put = client.send("PUT", objPath("c02.txt"), "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, put.status);
        String body = new String(put.body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"status\":\"SUCCESS\""));
        assertTrue(body.contains("c02.txt"));
    }

    @Test
    @DisplayName("CRUD-03 重复上传为覆盖写")
    void crud03_overwrite() throws Exception {
        client.send("PUT", objPath("c03.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        client.send("PUT", objPath("c03.txt"), "v2-longer".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("c03.txt"), null);
        assertArrayEquals("v2-longer".getBytes(StandardCharsets.UTF_8), get.body);
    }

    @Test
    @DisplayName("CRUD-04 删除后获取返回 404")
    void crud04_deleteThen404() throws Exception {
        client.send("PUT", objPath("c04.txt"), "gone".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, client.send("DELETE", objPath("c04.txt"), null).status);
        assertEquals(404, client.send("GET", objPath("c04.txt"), null).status);
    }

    @Test
    @DisplayName("CRUD-05 重复删除幂等成功")
    void crud05_deleteIdempotent() throws Exception {
        client.send("PUT", objPath("c05.txt"), "x".getBytes(StandardCharsets.UTF_8));
        client.send("DELETE", objPath("c05.txt"), null);
        assertEquals(200, client.send("DELETE", objPath("c05.txt"), null).status);
    }

    @Test
    @DisplayName("CRUD-06 多级子目录自动创建")
    void crud06_autoCreateDirs() throws Exception {
        HttpResponse put = client.send("PUT", objPath("a/b/c/deep.txt"), "deep".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, put.status);
        assertEquals(200, client.send("GET", objPath("a/b/c/deep.txt"), null).status);
    }

    @Test
    @DisplayName("CRUD-07 二进制内容（含 0 与负字节）一致")
    void crud07_binaryContent() throws Exception {
        byte[] data = new byte[]{0, 1, -1, 127, -128, 42};
        client.send("PUT", objPath("c07.bin"), data);
        HttpResponse get = client.send("GET", objPath("c07.bin"), null);
        assertArrayEquals(data, get.body);
    }

    @Test
    @DisplayName("CRUD-08 1MB 大文件抽样一致")
    void crud08_largeFile() throws Exception {
        byte[] big = new byte[1024 * 1024];
        new Random(7).nextBytes(big);
        assertEquals(200, client.send("PUT", objPath("c08-big.bin"), big).status);
        HttpResponse get = client.send("GET", objPath("c08-big.bin"), null);
        assertEquals(big.length, get.body.length);
        for (int i = 0; i < big.length; i += 509) {
            assertEquals(big[i], get.body[i], "位置 " + i + " 字节不一致");
        }
    }

    @Test
    @DisplayName("CRUD-09 中文文件名上传下载一致且 Disposition 按 RFC5987 编码")
    void crud09_chineseFileName() throws Exception {
        byte[] data = "中文内容".getBytes(StandardCharsets.UTF_8);
        assertEquals(200, client.send("PUT", objPath("c09-中文文档.txt"), data).status);
        HttpResponse get = client.send("GET", objPath("c09-中文文档.txt"), null);
        assertEquals(200, get.status);
        assertArrayEquals(data, get.body);
        String disposition = get.headers.getOrDefault("content-disposition", "");
        assertTrue(disposition.contains("filename*=UTF-8''"), "非 ASCII 文件名应有 RFC5987 编码形式");
    }

    @Test
    @DisplayName("CRUD-10 空格文件名上传下载一致")
    void crud10_spaceFileName() throws Exception {
        byte[] data = "spaced".getBytes(StandardCharsets.UTF_8);
        assertEquals(200, client.send("PUT", objPath("c10 file name.txt"), data).status);
        HttpResponse get = client.send("GET", objPath("c10 file name.txt"), null);
        assertEquals(200, get.status);
        assertArrayEquals(data, get.body);
    }

    /* ==================== 二、目录浏览 ==================== */

    @Test
    @DisplayName("DIR-01 桶根目录返回列表 JSON 结构完整")
    void dir01_rootListStructure() throws Exception {
        client.send("PUT", objPath("d01.txt"), "d".getBytes(StandardCharsets.UTF_8));
        HttpResponse list = client.send("GET", "/" + BUCKET, null);
        assertEquals(200, list.status);
        String body = new String(list.body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"bucket\":\"" + BUCKET + "\""));
        assertTrue(body.contains("\"path\":\"\""));
        assertTrue(body.contains("\"directory\":true") || body.contains("\"directory\":false"));
        assertTrue(body.contains("\"lastModified\":"));
    }

    @Test
    @DisplayName("DIR-02 列表 count 与条目数一致")
    void dir02_countMatchesItems() throws Exception {
        client.send("PUT", objPath("dir02/x1.txt"), "1".getBytes(StandardCharsets.UTF_8));
        client.send("PUT", objPath("dir02/x2.txt"), "2".getBytes(StandardCharsets.UTF_8));
        HttpResponse list = client.send("GET", objPath("dir02"), null);
        String body = new String(list.body, StandardCharsets.UTF_8);
        int count = Integer.parseInt(body.replaceAll(".*\"count\":(\\d+).*", "$1"));
        long items = body.split("\\{\"name\"").length - 1;
        assertEquals(items, count);
    }

    @Test
    @DisplayName("DIR-03 子目录以 directory=true 形式出现在父级列表")
    void dir03_subDirMarked() throws Exception {
        client.send("PUT", objPath("dir03/inner/f.txt"), "f".getBytes(StandardCharsets.UTF_8));
        HttpResponse list = client.send("GET", objPath("dir03"), null);
        String body = new String(list.body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"name\":\"inner\""));
        assertTrue(body.contains("\"directory\":true"));
    }

    @Test
    @DisplayName("DIR-04 尾斜杠显式目录访问返回列表")
    void dir04_trailingSlashList() throws Exception {
        client.send("PUT", objPath("dir04/a.txt"), "a".getBytes(StandardCharsets.UTF_8));
        HttpResponse list = client.send("GET", objPath("dir04") + "/", null);
        assertEquals(200, list.status);
        String body = new String(list.body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"path\":\"dir04\""));
        assertTrue(body.contains("a.txt"));
    }

    @Test
    @DisplayName("DIR-05 不存在的目录（无尾斜杠）返回 404")
    void dir05_missingDir404() throws Exception {
        assertEquals(404, client.send("GET", objPath("no-such-dir-xyz"), null).status);
    }

    @Test
    @DisplayName("DIR-06 尾斜杠访问不存在的目录返回空列表而非 404")
    void dir06_missingDirTrailingSlashEmptyList() throws Exception {
        HttpResponse list = client.send("GET", objPath("no-such-dir-xyz") + "/", null);
        assertEquals(200, list.status);
        String body = new String(list.body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"count\":0"));
    }

    @Test
    @DisplayName("DIR-07 多级目录逐级导航")
    void dir07_multiLevelNav() throws Exception {
        client.send("PUT", objPath("nav/l1/l2/leaf.txt"), "leaf".getBytes(StandardCharsets.UTF_8));
        HttpResponse level1 = client.send("GET", objPath("nav"), null);
        assertTrue(new String(level1.body, StandardCharsets.UTF_8).contains("l1"));
        HttpResponse level2 = client.send("GET", objPath("nav/l1"), null);
        assertTrue(new String(level2.body, StandardCharsets.UTF_8).contains("l2"));
        HttpResponse level3 = client.send("GET", objPath("nav/l1/l2"), null);
        assertTrue(new String(level3.body, StandardCharsets.UTF_8).contains("leaf.txt"));
    }

    /* ==================== 三、预览与下载模式 ==================== */

    @Test
    @DisplayName("MODE-01 默认内联预览（inline）")
    void mode01_defaultInline() throws Exception {
        client.send("PUT", objPath("m01.txt"), "m".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("m01.txt"), null);
        assertTrue(get.headers.getOrDefault("content-disposition", "").startsWith("inline"));
    }

    @Test
    @DisplayName("MODE-02 download 强制附件（标志式与 =true 等价）")
    void mode02_downloadAttachment() throws Exception {
        client.send("PUT", objPath("m02.txt"), "m".getBytes(StandardCharsets.UTF_8));
        HttpResponse withValue = client.send("GET", objPath("m02.txt") + "?download=true", null);
        HttpResponse flagOnly = client.send("GET", objPath("m02.txt") + "?download", null);
        assertTrue(withValue.headers.getOrDefault("content-disposition", "").startsWith("attachment"));
        assertTrue(flagOnly.headers.getOrDefault("content-disposition", "").startsWith("attachment"),
                "?download 无值形式应等价于 download=true");
    }

    @Test
    @DisplayName("MODE-05 preview 显式内联（标志式与 =true 等价）")
    void mode05_explicitPreview() throws Exception {
        client.send("PUT", objPath("m05.txt"), "m".getBytes(StandardCharsets.UTF_8));
        HttpResponse flagOnly = client.send("GET", objPath("m05.txt") + "?preview", null);
        HttpResponse withValue = client.send("GET", objPath("m05.txt") + "?preview=true", null);
        assertEquals(200, flagOnly.status);
        assertTrue(flagOnly.headers.getOrDefault("content-disposition", "").startsWith("inline"),
                "?preview 无值形式应为内联");
        assertTrue(withValue.headers.getOrDefault("content-disposition", "").startsWith("inline"));
    }

    @Test
    @DisplayName("TYPE-01 json 扩展名推断 application/json")
    void type01_json() throws Exception {
        client.send("PUT", objPath("t01.json"), "{}".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("t01.json"), null);
        assertTrue(get.headers.getOrDefault("content-type", "").contains("application/json"));
    }

    @Test
    @DisplayName("TYPE-02 png 扩展名推断 image/png")
    void type02_png() throws Exception {
        client.send("PUT", objPath("t02.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        HttpResponse get = client.send("GET", objPath("t02.png"), null);
        assertTrue(get.headers.getOrDefault("content-type", "").contains("image/png"));
    }

    @Test
    @DisplayName("TYPE-03 html 扩展名推断 text/html")
    void type03_html() throws Exception {
        client.send("PUT", objPath("t03.html"), "<p/>".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("t03.html"), null);
        assertTrue(get.headers.getOrDefault("content-type", "").contains("text/html"));
    }

    @Test
    @DisplayName("TYPE-04 未知扩展名回退 application/octet-stream")
    void type04_unknownFallback() throws Exception {
        client.send("PUT", objPath("t04.xyzabc"), "bin".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("t04.xyzabc"), null);
        assertTrue(get.headers.getOrDefault("content-type", "").contains("application/octet-stream"));
    }

    /* ==================== 四、Range 分段 ==================== */

    @Test
    @DisplayName("RANGE-01 区间 bytes=3-7 返回 206 与正确分片")
    void range01_partial() throws Exception {
        client.send("PUT", objPath("r01.bin"), new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        HttpResponse get = client.send("GET", objPath("r01.bin"), null,
                java.util.Map.of("Range", "bytes=3-7"));
        assertEquals(206, get.status);
        assertArrayEquals(new byte[]{3, 4, 5, 6, 7}, get.body);
        assertEquals("bytes 3-7/10", get.headers.get("content-range"));
    }

    @Test
    @DisplayName("RANGE-02 后缀 bytes=-4 取末尾 N 字节")
    void range02_suffix() throws Exception {
        client.send("PUT", objPath("r02.bin"), new byte[]{0, 1, 2, 3, 4, 5});
        HttpResponse get = client.send("GET", objPath("r02.bin"), null,
                java.util.Map.of("Range", "bytes=-4"));
        assertEquals(206, get.status);
        assertArrayEquals(new byte[]{2, 3, 4, 5}, get.body);
    }

    @Test
    @DisplayName("RANGE-03 无 Range 返回全量 200")
    void range03_fullWithoutRange() throws Exception {
        byte[] data = "full-body".getBytes(StandardCharsets.UTF_8);
        client.send("PUT", objPath("r03.bin"), data);
        HttpResponse get = client.send("GET", objPath("r03.bin"), null);
        assertEquals(200, get.status);
        assertArrayEquals(data, get.body);
    }

    @Test
    @DisplayName("RANGE-04 起始越界时降级为全量 200")
    void range04_outOfRangeFallback() throws Exception {
        client.send("PUT", objPath("r04.bin"), "12345".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("r04.bin"), null,
                java.util.Map.of("Range", "bytes=99-100"));
        assertEquals(200, get.status);
        assertEquals(5, get.body.length);
    }

    @Test
    @DisplayName("RANGE-05 非法 Range 头降级为全量 200")
    void range05_illegalRangeFallback() throws Exception {
        client.send("PUT", objPath("r05.bin"), "abcdef".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("r05.bin"), null,
                java.util.Map.of("Range", "bytes=abc-def"));
        assertEquals(200, get.status);
        assertEquals(6, get.body.length);
    }

    @Test
    @DisplayName("RANGE-06 子目录对象的分段下载")
    void range06_nestedObjectRange() throws Exception {
        client.send("PUT", objPath("r06-dir/in.bin"), new byte[]{10, 20, 30, 40});
        HttpResponse get = client.send("GET", objPath("r06-dir/in.bin"), null,
                java.util.Map.of("Range", "bytes=1-2"));
        assertEquals(206, get.status);
        assertArrayEquals(new byte[]{20, 30}, get.body);
    }

    /* ==================== 五、存在性检查 ==================== */

    @Test
    @DisplayName("HEAD-01 存在的对象返回 200")
    void head01_exists() throws Exception {
        client.send("PUT", objPath("h01.txt"), "h".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, client.send("HEAD", objPath("h01.txt"), null).status);
    }

    @Test
    @DisplayName("HEAD-02 不存在的对象返回 404")
    void head02_missing() throws Exception {
        assertEquals(404, client.send("HEAD", objPath("h01-not-exist.txt"), null).status);
    }

    @Test
    @DisplayName("HEAD-03 子目录对象检查正常")
    void head03_nested() throws Exception {
        client.send("PUT", objPath("h-dir/nested.txt"), "n".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, client.send("HEAD", objPath("h-dir/nested.txt"), null).status);
    }

    /* ==================== 六、水印 ==================== */

    @Test
    @DisplayName("WATERMARK-01 主服务器响应携带配置的水印头")
    void wm01_watermarkHeader() throws Exception {
        client.send("PUT", objPath("wm.txt"), "wm".getBytes(StandardCharsets.UTF_8));
        HttpResponse get = client.send("GET", objPath("wm.txt"), null);
        assertEquals("CONFIDENTIAL", get.headers.get("x-watermark"));
    }

    @Test
    @DisplayName("WATERMARK-02 严格模式不携带水印头")
    void wm02_strictNoWatermark() throws Exception {
        client.sendTo(strictPort, "PUT", objPath("wm-s.txt"), "s".getBytes(), java.util.Map.of());
        HttpResponse get = client.sendTo(strictPort, "GET", objPath("wm-s.txt"), null, java.util.Map.of());
        assertNull(get.headers.get("x-watermark"));
    }

    /* ==================== 七、访问模式收敛 ==================== */

    @Test
    @DisplayName("MODE-03 严格模式下预览与下载均关闭返回 403")
    void mode03_strictForbidden() throws Exception {
        client.sendTo(strictPort, "PUT", objPath("locked.txt"), "locked".getBytes(), java.util.Map.of());
        assertEquals(403, client.sendTo(strictPort, "GET", objPath("locked.txt"), null, java.util.Map.of()).status);
        assertEquals(403, client.sendTo(strictPort, "GET", objPath("locked.txt") + "?download=true", null, java.util.Map.of()).status);
    }

    @Test
    @DisplayName("MODE-04 严格模式下预览下载均关闭，Range 请求同样被 403 拒绝")
    void mode04_strictRejectRange() throws Exception {
        client.sendTo(strictPort, "PUT", objPath("sr.bin"), "0123456789".getBytes(), java.util.Map.of());
        HttpResponse get = client.sendTo(strictPort, "GET", objPath("sr.bin"), null,
                java.util.Map.of("Range", "bytes=0-3"));
        assertEquals(403, get.status);
    }

    /* ==================== 八、远程文件透传 ==================== */

    @Test
    @DisplayName("REMOTE-01 关闭透传时 http(s) Key 因冒号校验拒绝 400")
    void remote01_disabledReject() throws Exception {
        HttpResponse resp = client.send("GET", "/" + BUCKET + "/http:/evil.example.com/payload", null);
        assertEquals(400, resp.status);
    }

    @Test
    @DisplayName("REMOTE-02 开启透传时从远程源拉取内容")
    void remote02_enabledFetch() throws Exception {
        Server remoteProxyServer = null;
        try {
            FileStorageServerFilter enabledFilter = new FilterBuilder()
                    .bucket("remote-test").remote(true).build();
            int p = freePort();
            remoteProxyServer = newServer(p, enabledFilter);
            remoteProxyServer.start();
            Thread.sleep(300);

            HttpResponse resp = client.sendTo(p, "GET",
                    "/local/" + enc("http://127.0.0.1:" + remotePort + "/payload.bin"), null, java.util.Map.of());
            assertEquals(200, resp.status);
            assertArrayEquals(REMOTE_BYTES, resp.body);
        } finally {
            if (remoteProxyServer != null) {
                remoteProxyServer.stop();
            }
        }
    }

    /* ==================== 九、安全防护 ==================== */

    @Test
    @DisplayName("SEC-01 路径穿越 ..%2F 拒绝 400")
    void sec01_pathTraversal() throws Exception {
        assertEquals(400, client.send("GET",
                "/" + BUCKET + "/%2E%2E%2F%2E%2E%2Fetc%2Fpasswd", null).status);
    }

    @Test
    @DisplayName("SEC-02 解码后的 .. 片段拒绝 400")
    void sec02_dotDotSegment() throws Exception {
        assertEquals(400, client.send("GET",
                "/" + BUCKET + "/safe/%2E%2E/secret.txt", null).status);
    }

    @Test
    @DisplayName("SEC-03 空 body 上传拒绝 400")
    void sec03_emptyBody() throws Exception {
        assertEquals(400, client.send("PUT", objPath("sec-empty.txt"), new byte[0]).status);
    }

    @Test
    @DisplayName("SEC-04 缺少 Key 的 PUT 拒绝 400")
    void sec04_putWithoutKey() throws Exception {
        HttpResponse resp = client.send("PUT", "/" + BUCKET + "/", "data".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, resp.status);
    }

    @Test
    @DisplayName("SEC-05 未支持的方法返回 405")
    void sec05_methodNotAllowed() throws Exception {
        client.send("PUT", objPath("sec-m.txt"), "m".getBytes(StandardCharsets.UTF_8));
        assertEquals(405, client.send("PATCH", objPath("sec-m.txt"), "x".getBytes(StandardCharsets.UTF_8)).status);
    }

    @Test
    @DisplayName("SEC-06 未注册存储桶放行且不带业务头")
    void sec06_unknownBucketPassthrough() throws Exception {
        HttpResponse resp = client.send("GET", "/unknown-bucket-zz/file.txt", null);
        assertEquals(404, resp.status);
        assertFalse(resp.headers.containsKey("x-watermark"));
    }
}
