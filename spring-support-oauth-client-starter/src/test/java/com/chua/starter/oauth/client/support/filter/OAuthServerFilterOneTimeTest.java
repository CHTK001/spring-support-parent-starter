// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.filter;

import com.chua.common.support.network.http.HttpHeader;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OAuthServerFilter} 一次性链接（{@code /ot}）鉴权回归测试。
 *
 * <p>守护的修复是一个 P0 未授权读取：{@code resolveBucket} 对首段 {@code ot}
 * 返回 空（注释写的是「一次性链接路径本身即凭证，始终放行」），于是
 * {@code POST /ot?bucket=<私有桶>&key=<已知 key>} 被整段放行到
 * {@code FileStorageServerFilter#handleOneTime}，而后者只查对象存在与否，
 * 不做任何鉴权 —— 任何人可为私有桶中任意已知 key 换到一条免鉴权的
 * {@code /ot/{token}} 直链并读到文件内容。</p>
 *
 * <p>修复后：只有「消费」（{@code GET /ot/{token}}）免鉴权（那时令牌本身已是凭证，
 * 且只有鉴权通过的人才签得出令牌），「签发」（{@code POST /ot}）按目标桶走常规鉴权。
 * 这正是把令牌从查询参数换到一次性票据所依赖的前提 —— 票据本身必须是可信的。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@DisplayName("OAuthServerFilter: /ot 一次性链接鉴权")
class OAuthServerFilterOneTimeTest {

    // ==================== P0 核心反例：未认证不得签发私有桶的一次性链接 ====================

    @Test
    @DisplayName("P0 反例：未带令牌 POST /ot?bucket=私有桶 不得放行到签发逻辑")
    void mintForPrivateBucketWithoutTokenIsRejected() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        StubRequest request = new StubRequest(HttpMethod.POST, "/ot", Map.of("bucket", "private-bucket", "key", "secret.pdf"));
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(0, chainCalls.get(), "未认证请求不得触达签发逻辑");
        assertEquals(401, response.getStatus());
        assertTrue(response.ended, "拒绝时必须结束响应，否则链会继续下发 200");
    }

    @Test
    @DisplayName("P0 反例：Bearer 头以外的方式也不能绕过 —— 查询参数里的令牌同样进入校验")
    void mintForPrivateBucketWithUnknownTokenIsRejected() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        // 带了一个令牌但鉴权协议未注册 → 鉴权失败，仍须拒绝（证明确实走到了校验分支）
        StubRequest request = new StubRequest(HttpMethod.POST, "/ot",
                Map.of("bucket", "private-bucket", "key", "secret.pdf"));
        request.header("x-oauth-token", "attacker-token");
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(0, chainCalls.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("P0 反例：路径段大小写变体（/OT/）不得重新打开绕过口子")
    void mintIsGatedCaseInsensitively() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        StubRequest request = new StubRequest(HttpMethod.POST, "/OT", Map.of("bucket", "private-bucket", "key", "k"));
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(0, chainCalls.get(), "首段判定必须大小写不敏感");
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("P0 反例：缺 bucket 参数时拒绝，而不是当成公共桶放行")
    void mintWithoutBucketIsRejected() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        StubRequest request = new StubRequest(HttpMethod.POST, "/ot", Map.of("key", "secret.pdf"));
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(0, chainCalls.get(), "空 bucket 不得回落到「公共」分支");
        assertEquals(400, response.getStatus());
    }

    // ==================== 必须保持放行的路径（防止修复过头打断既有功能） ====================

    @Test
    @DisplayName("消费 GET /ot/{token} 仍免鉴权：令牌本身即凭证")
    void consumeOneTimeLinkStaysExempt() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        StubRequest request = new StubRequest(HttpMethod.GET, "/ot/6f1c0a9e2b7d4c11", Map.of());
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get(), "消费请求必须放行，否则签发出来的链接立刻失效");
        assertFalse(response.ended);
    }

    @Test
    @DisplayName("为公共桶签发仍放行：私有化只影响私有桶，不牵连公共桶")
    void mintForPublicBucketStaysExempt() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        StubRequest request = new StubRequest(HttpMethod.POST, "/ot", Map.of("bucket", "public-bucket", "key", "a.png"));
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get());
        assertFalse(response.ended);
    }

    @Test
    @DisplayName("原有私有桶直读鉴权未被改动：无令牌仍 401")
    void privateBucketDirectReadStillRequiresToken() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        StubRequest request = new StubRequest(HttpMethod.GET, "/private-bucket/secret.pdf", Map.of());
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(0, chainCalls.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("通配私有桶 * 下，签发同样受鉴权约束")
    void mintIsGatedUnderWildcardPrivateBuckets() throws Exception {
        OAuthServerFilter filter = new OAuthServerFilter();
        filter.setPrivateBuckets(Set.of("*"));
        StubRequest request = new StubRequest(HttpMethod.POST, "/ot", Map.of("bucket", "anything", "key", "k"));
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(0, chainCalls.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("非 /ot 的管理面请求（无桶名段）行为不变：仍然放行")
    void nonBucketPathsAreUnaffected() throws Exception {
        OAuthServerFilter filter = filterWithPrivateBuckets("private-bucket");
        StubRequest request = new StubRequest(HttpMethod.GET, "/tree", Map.of());
        StubResponse response = new StubResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get());
    }

    private static OAuthServerFilter filterWithPrivateBuckets(String... buckets) {
        OAuthServerFilter filter = new OAuthServerFilter();
        filter.setPrivateBuckets(Set.of(buckets));
        return filter;
    }

    // ==================== 测试替身（刻意不引 Mockito：接口简单，打桩只会掩盖真实取值路径） ====================

    private static final class StubRequest implements ServerRequest {
        private final HttpMethod method;
        private final String path;
        private final Map<String, String> params;
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private StubRequest(HttpMethod method, String path, Map<String, String> params) {
            this.method = method;
            this.path = path;
            this.params = params;
        }

        private void header(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public String getUri() {
            return path;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public HttpHeader getHeaders() {
            return null;
        }

        @Override
        public Map<String, String> getParams() {
            return params;
        }

        @Override
        public String getParam(String name) {
            return params.get(name);
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public byte[] getBody() {
            return new byte[0];
        }

        @Override
        public String getBodyString() {
            return "";
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public String getRemoteAddress() {
            return "127.0.0.1";
        }

        @Override
        public int getRemotePort() {
            return 12345;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }
    }

    private static final class StubResponse implements ServerResponse {
        private int status = 200;
        private String contentType;
        private byte[] body = new byte[0];
        private final Map<String, String> headers = new LinkedHashMap<>();
        private boolean ended;

        @Override
        public ServerResponse setStatus(int statusCode) {
            this.status = statusCode;
            return this;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public ServerResponse setHeader(String name, String value) {
            headers.put(name, value);
            return this;
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public HttpHeader getHeaders() {
            return null;
        }

        @Override
        public ServerResponse setContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public ServerResponse setBody(byte[] body) {
            this.body = body;
            return this;
        }

        @Override
        public ServerResponse setBody(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        @Override
        public byte[] getBody() {
            return body;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public ServerResponse sendRedirect(String location) {
            this.status = 302;
            headers.put("Location", location);
            return this;
        }

        @Override
        public ServerResponse sendError(int statusCode, String message) {
            this.status = statusCode;
            this.body = message == null ? new byte[0] : message.getBytes(StandardCharsets.UTF_8);
            this.ended = true;
            return this;
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public boolean isCommitted() {
            return ended;
        }

        @Override
        public boolean isEnded() {
            return ended;
        }

        @Override
        public ServerResponse reset() {
            this.status = 200;
            this.body = new byte[0];
            this.ended = false;
            return this;
        }

        @Override
        public void end() {
            this.ended = true;
        }

        @Override
        public void writeRaw(byte[] bytes) {
            this.body = bytes;
        }
    }}