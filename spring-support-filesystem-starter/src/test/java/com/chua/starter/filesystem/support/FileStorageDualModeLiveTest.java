package com.chua.starter.filesystem.support;

import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageProperties.ProviderConfig;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双模式（embedded / standalone）文件服务器验收。
 *
 * <p>standalone 形态做一次<b>真·HTTP 活体</b>往返：{@link FileStorageServerLauncher}
 * 另起独立 HTTP 服务，{@code /{桶}/{键}} 完成 PUT/GET，证明独立形态端到端可用。</p>
 *
 * <p>embedded 形态与 standalone 共用同一份 {@code FileStorageServerFilter} 核心
 * （见 {@code FileStorageEmbeddedFilter} 与 {@code FileStorageServerAssembler}），
 * 其 Servlet 适配行为（前缀剥离 / 放行边界 / 响应一次性写出）由
 * {@code FileStorageEmbeddedFilterTest} 覆盖，URL 规则与配置下发由
 * {@code FileServerConfigControllerTest} / {@code FileSystemAutoConfigurationModeTest}
 * 覆盖 —— 本类只承担「独立端口活体」这一块。</p>
 *
 * <p>不依赖外部后端 / 数据库 / 共享端口：本地存储落到临时目录，端口每次随机取空闲端口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileStorageDualModeLiveTest {

    /**
     * 独立形态：另起 HTTP 服务，/{桶}/{键} 完成 PUT/GET 往返。
     */
    @Test
    void standaloneModeServesFilesOverHttp(@TempDir Path root) throws IOException {
        FileStorageTemplate template = template(root);
        int port = freePort();
        FileStorageServerProperties sp = new FileStorageServerProperties();
        sp.setMode(FileStorageServerProperties.Mode.STANDALONE);
        sp.setHost("127.0.0.1");
        sp.setPort(port);

        FileStorageServerLauncher launcher = new FileStorageServerLauncher(sp, template);
        launcher.start();
        assertTrue(launcher.isRunning(), "独立形态文件服务器应已启动");

        HttpClient http = client();
        String body = "dual-standalone " + System.nanoTime();
        String url = "http://127.0.0.1:" + port + "/local/accept/standalone.txt";

        try {
            int put = status(httpPut(http, url, body));
            assertTrue(put >= 200 && put < 300, "standalone PUT 应成功，实际 " + put);
            HttpResponse<String> get = httpGet(http, url);
            assertEquals(200, get.statusCode(), "standalone GET 应 200");
            assertEquals(body, get.body(), "standalone GET 内容应与 PUT 一致");
        } finally {
            launcher.close();
            template.close();
        }
    }

    /**
     * 本地文件存储模板（provider 名 local，桶 local，根目录指向临时目录）。
     */
    private static FileStorageTemplate template(Path root) {
        FileStorageProperties p = new FileStorageProperties();
        p.setEnable(true);
        p.setDefaultProvider("local");
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        ProviderConfig local = new ProviderConfig();
        local.setBasePath(root.toFile().getAbsolutePath());
        local.setBucket("local");
        providers.put("local", local);
        p.setProviders(providers);
        FileStorageTemplate template = new FileStorageTemplate(p);
        template.initialize();
        return template;
    }

    /**
     * 取一个当前空闲端口。
     */
    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("无法获取空闲端口", e);
        }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private static HttpResponse<String> httpPut(HttpClient http, String url, String body) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/octet-stream")
                .build();
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static HttpResponse<String> httpGet(HttpClient http, String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static int status(HttpResponse<String> r) {
        return r.statusCode();
    }
}
