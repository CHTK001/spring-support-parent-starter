package com.chua.starter.filesystem.support.server.embedded;

import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置形态（{@link FileStorageEmbeddedFilter}）的路径与响应适配测试。
 *
 * <p>覆盖三件事：</p>
 * <ol>
 *   <li>前缀剥离：{@code /fs/local/a.txt} 必须交给存储层 {@code /local/a.txt}，
 *       且不能误吞 {@code /fsx/...} 这类同前缀兄弟路径；</li>
 *   <li>路由边界：前缀内由文件服务器兜底（不回落到应用路由），前缀外交给后续过滤器；</li>
 *   <li>响应适配：状态码 / 响应头 / 响应体按「结束时一次性写出」落到 Servlet 响应。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileStorageEmbeddedFilterTest {

    /**
     * 前缀剥离：普通对象路径。
     */
    @Test
    void resolvePathStripsPrefix() {
        assertEquals("/local/a.txt", FileStorageEmbeddedFilter.resolvePath("/fs", "/fs/local/a.txt"));
        assertEquals("/local/中文 目录/a.pdf", FileStorageEmbeddedFilter.resolvePath("/fs", "/fs/local/中文 目录/a.pdf"));
    }

    /**
     * 前缀剥离：请求的正是前缀本身时等价于桶根。
     */
    @Test
    void resolvePathMapsPrefixToBucketRoot() {
        assertEquals("", FileStorageEmbeddedFilter.resolvePath("/fs", "/fs"));
    }

    /**
     * 前缀剥离：不能误吞同前缀的兄弟路径（{@code /fsx} 不是 {@code /fs} 之下）。
     */
    @Test
    void resolvePathRejectsSiblingPrefix() {
        assertNull(FileStorageEmbeddedFilter.resolvePath("/fs", "/fsx/a.txt"));
        assertNull(FileStorageEmbeddedFilter.resolvePath("/fs", "/other/local/a.txt"));
    }

    /**
     * 前缀为空时占用根路径，任何请求都归文件服务器。
     */
    @Test
    void resolvePathWithoutPrefixAcceptsEverything() {
        assertEquals("/local/a.txt", FileStorageEmbeddedFilter.resolvePath("", "/local/a.txt"));
    }

    /**
     * 前缀之外的请求必须原样放行给应用自己的路由。
     */
    @Test
    void passesThroughPathsOutsidePrefix() throws Exception {
        FileStorageEmbeddedFilter filter = buildFilter("embedded-filter-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "放行的请求不应被文件服务器改写状态");
        assertTrue(chain.getRequest() != null, "前缀之外的请求必须继续走后续过滤器");
    }

    /**
     * 前缀之内的请求不再回落到应用路由：即使桶不存在，也由文件服务器给出错误响应。
     */
    @Test
    void handlesPathsInsidePrefixWithoutFallingThrough() throws Exception {
        FileStorageEmbeddedFilter filter = buildFilter("embedded-filter-2");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fs/local/missing.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // 未知桶由文件存储过滤器自行给出 4xx/5xx（当前为 503），关键是**不再回落**到应用路由
        assertTrue(response.getStatus() >= 400,
                "未知桶必须返回错误状态，实际 " + response.getStatus());
        assertNull(chain.getRequest(), "前缀之内的请求不应回落到应用路由");
    }

    /**
     * 响应适配：状态码 / 头 / 体一次性写出。
     */
    @Test
    void responseAdapterWritesStatusHeadersAndBody() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerResponse response = new ServletServerResponse(servletResponse);
        byte[] body = "hello 文件".getBytes(StandardCharsets.UTF_8);

        response.setStatus(201)
                .setHeader("X-Test", "1")
                .setContentType("text/plain;charset=UTF-8")
                .setBody(body)
                .end();

        assertTrue(response.isEnded());
        assertEquals(201, servletResponse.getStatus());
        assertEquals("1", servletResponse.getHeader("X-Test"));
        assertTrue(servletResponse.getContentType().startsWith("text/plain"));
        assertArrayEquals(body, servletResponse.getContentAsByteArray());
    }

    /**
     * 未显式结束时，错误响应也必须真正写出（避免只改状态码、响应体为空）。
     */
    @Test
    void responseAdapterSendErrorWritesBody() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerResponse response = new ServletServerResponse(servletResponse);

        response.sendError(404, "Not Found");

        assertEquals(404, servletResponse.getStatus());
        assertEquals("Not Found", new String(servletResponse.getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    /**
     * 构造被测过滤器（无存储桶，便于验证路由边界）。
     *
     * @param name 测试名（用于区分日志）
     * @return 过滤器
     */
    private static FileStorageEmbeddedFilter buildFilter(String name) {
        FileStorageProperties storageProperties = new FileStorageProperties();
        FileStorageTemplate template = new FileStorageTemplate(storageProperties);
        template.initialize();

        FileStorageServerProperties serverProperties = new FileStorageServerProperties();
        serverProperties.setPathPrefix("/fs");
        FileStorageEmbeddedFilter filter = new FileStorageEmbeddedFilter(serverProperties, template);
        assertFalse(filter.getPathPrefix().isEmpty(), name + ": 前缀应已规范化");
        return filter;
    }
}
