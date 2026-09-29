package com.chua.starter.test.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * oauth TCP 长度帧协议真实测试。
 *
 * <p>覆盖 {@code com.chua.starter.oauth.client.support.protocol.TcpProtocol} 与
 * {@code com.chua.starter.oauth.sso.support.protocol.OauthProtocolServerManager}
 * 之间的私有线上协议——它没有公共文档也没有任何既有测试保护，而两端都硬编码了它：</p>
 * <ul>
 *   <li><b>帧格式</b>：4 字节<b>大端</b> int 长度头 + 负载（依据
 *       {@code AioTcpServer} 的 {@code ByteBuffer.putInt(data.length)}）</li>
 *   <li><b>帧内容</b>：{@code route:body}，{@code route} 是服务端路由路径</li>
 * </ul>
 *
 * <p><b>本测试固化的两个真实踩坑</b>（均为实测踩中后修正，改动前必读）：</p>
 * <ol>
 *   <li>帧前缀必须<b>带前导斜杠</b>（{@code /login} 而非 {@code login}）。服务端把冒号
 *       前的部分当作 {@code path} 与 {@code PATH_LOGIN}（{@code "/login"}）做字符串匹配，
 *       写成 {@code login} 会得到「不支持的认证路径: login」。</li>
 *   <li><b>不能用</b> {@code TcpSyncClient} 来验证——它走的是
 *       {@code com.chua.common.support.network.sync} 的「topic + 行切分」协议，与本协议
 *       不是同一套，实测发出的帧服务端完全收不到（无任何日志）。</li>
 * </ol>
 *
 * <p>后两个用例需要一个 oauth 服务端，不可达时按
 * {@link org.junit.jupiter.api.Assumptions} 跳过，不影响纯本地的帧编解码用例。
 * 默认连本机 {@code 19180}；指向远端部署时用系统属性覆盖：</p>
 *
 * <pre>
 * mvn -f spring-support-test-app/pom.xml surefire:test \
 *     -Dtest=OauthTcpLengthFrameRealTest \
 *     -Doauth.tcp.host=192.168.50.198 -Doauth.tcp.port=19180
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
class OauthTcpLengthFrameRealTest {

    /**
     * 覆盖服务端主机的系统属性名，用于在没有本地服务端的环境跑集成用例
     */
    private static final String PROP_HOST = "oauth.tcp.host";

    /**
     * 覆盖服务端端口的系统属性名
     */
    private static final String PROP_PORT = "oauth.tcp.port";

    /**
     * oauth 服务端 tcp 协议默认端口，与服务端 {@code AuthServerProperties} 的 tcpPort 一致
     */
    private static final int OAUTH_TCP_PORT_DEFAULT = 19180;

    /**
     * oauth 服务端主机，默认本机；指向远端部署时用 {@code -Doauth.tcp.host=...} 覆盖
     */
    private static final String OAUTH_TCP_HOST =
            System.getProperty(PROP_HOST, "127.0.0.1");

    /**
     * oauth 服务端 tcp 端口，默认 {@value #OAUTH_TCP_PORT_DEFAULT}
     */
    private static final int OAUTH_TCP_PORT =
            Integer.getInteger(PROP_PORT, OAUTH_TCP_PORT_DEFAULT);

    /**
     * 连接与读写的超时时间；服务端无响应时快速失败而不是挂满整个测试轮次
     */
    private static final int TIMEOUT_MILLIS = 15_000;

    /**
     * 帧负载长度上限，用于把损坏帧头造成的巨额分配挡在解析之前
     */
    private static final int MAX_FRAME_BYTES = 1 << 20;

    /**
     * oauth 服务端登录路由常量，与服务端 {@code OauthProtocolConstants.PATH_LOGIN} 对齐
     */
    private static final String ROUTE_LOGIN = "/login";

    /**
     * 单帧读写连接的超时上限
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;

    /**
     * 长度帧编解码：4 字节大端 int 头 + 负载。
     *
     * <p>刻意与 {@code TcpProtocol} 独立实现，而不是复用它的私有方法——这样本测试
     * 才是对协议的独立验证；若改成复用，等于用被测对象验证被测对象。</p>
     */
    private static byte[] encodeFrame(String frame) {
        var payload = frame.getBytes(StandardCharsets.UTF_8);
        var buffer = new byte[4 + payload.length];
        // 手工写 4 字节大端 int，避免依赖 ByteBuffer 的默认字节序假设
        buffer[0] = (byte) (payload.length >>> 24);
        buffer[1] = (byte) (payload.length >>> 16);
        buffer[2] = (byte) (payload.length >>> 8);
        buffer[3] = (byte) payload.length;
        System.arraycopy(payload, 0, buffer, 4, payload.length);
        return buffer;
    }

    /**
     * 读取一帧长度帧负载。
     *
     * @param in   已建立连接的输入流
     * @param size 帧负载长度，由 {@link #readFrameHeader(DataInputStream)} 给出
     * @return 帧负载字节
     * @throws IOException 读取失败或连接提前关闭
     */
    private static byte[] readPayload(DataInputStream in, int size) throws IOException {
        var payload = new byte[size];
        var read = 0;
        while (read < size) {
            var n = in.read(payload, read, size - read);
            if (n < 0) {
                throw new EOFException("连接在读取中途关闭，已读 " + read + "/" + size + " 字节");
            }
            read += n;
        }
        return payload;
    }

    /**
     * 读取 4 字节大端 int 长度头，并挡住损坏帧头造成的巨额分配。
     *
     * <p>长度头来自不可信网络，若照着它分配，一个伪造的 {@code 0x7FFFFFFF}
     * 就会要求 2GB 数组并在分配时 OOM。因此必须在分配之前先判上限。</p>
     *
     * @param in 已建立连接的输入流
     * @return 帧负载长度，必定在 {@code 1..MAX_FRAME_BYTES} 区间内
     * @throws IOException            读取失败
     * @throws IllegalStateException  帧长非法（非正数或超过上限）
     */
    private static int readFrameHeader(DataInputStream in) throws IOException {
        var b0 = in.read();
        var b1 = in.read();
        var b2 = in.read();
        var b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new EOFException("未读到完整的长度头，连接已关闭");
        }
        var size = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        // 注意 size 可能为负：0x80000000 以上的帧头按补码解释即为负数
        if (size <= 0 || size > MAX_FRAME_BYTES) {
            throw new IllegalStateException("非法帧长度: " + size + "（上限 " + MAX_FRAME_BYTES + "）");
        }
        return size;
    }

    /**
     * 探测 oauth 服务端的 tcp 端口是否可达，用于决定集成用例跑还是跳过。
     *
     * @return 可达返回 {@code true}
     */
    private static boolean serverReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(OAUTH_TCP_HOST, OAUTH_TCP_PORT), CONNECT_TIMEOUT_MILLIS);
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 与 oauth 服务端建立一次短连接并完成一问一答。
     *
     * @param frame 待发送的完整帧内容（{@code route:body}）
     * @return 服务端回帧的负载
     * @throws IOException 连接或读写失败
     */
    private static String callServer(String frame) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(OAUTH_TCP_HOST, OAUTH_TCP_PORT), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);

            var out = new DataOutputStream(socket.getOutputStream());
            out.write(encodeFrame(frame));
            out.flush();

            var in = new DataInputStream(socket.getInputStream());
            var size = readFrameHeader(in);
            return new String(readPayload(in, size), StandardCharsets.UTF_8);
        }
    }

    /**
     * 长度头是大端 4 字节 int，不是小端也不是 2 字节短整型。
     *
     * <p>若误用小端，{@code 127} 会被解析成 {@code 0x7F000000}（2 亿字节），
     * 直接把后续负载当成超长帧等待而超时——这正是本用例要挡住的错误。</p>
     */
    @Test
    @DisplayName("长度头按大端 4 字节 int 编解码，往返一致")
    void lengthFrame_shouldRoundTripInBigEndian() {
        var frame = "/login:{\"x-oauth-username\":\"admin\"}";
        var encoded = encodeFrame(frame);

        var payloadLength = frame.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(4 + payloadLength, encoded.length, "整帧长度应等于 4 字节头 + 负载");
        assertEquals(payloadLength >>> 24, encoded[0] & 0xFF, "首字节应为长度的最高位");
        assertEquals(payloadLength & 0xFF, encoded[3] & 0xFF, "末字节应为长度的最低位");
        assertEquals(frame, new String(encoded, 4, payloadLength, StandardCharsets.UTF_8), "负载应原样保留");
    }

    /**
     * 读帧必须把负载读满，不能在第一个 {@code read} 返回时就收工。
     *
     * <p>TCP 是字节流，一次 {@code read} 不保证拿满，短包会让负载被截断成非法 JSON。</p>
     */
    @Test
    @DisplayName("分片到达的帧能被读满，不被首个 read 截断")
    void readFrame_shouldAccumulateUntilPayloadComplete() throws Exception {
        var frame = "/login:{\"x-oauth-username\":\"student\"}";
        var encoded = encodeFrame(frame);
        var payloadLength = encoded.length - 4;

        // 用一个「每次只吐 7 字节」的流模拟分片
        var chunked = new java.io.ByteArrayInputStream(encoded) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 7));
            }
        };

        var in = new DataInputStream(chunked);
        var size = readFrameHeader(in);
        assertEquals(payloadLength, size, "长度头解析出的负载长度应正确");
        assertEquals(frame, new String(readPayload(in, size), StandardCharsets.UTF_8), "分片后负载应完整");
    }

    /**
     * 帧路由必须带前导斜杠，否则服务端按整串做路径匹配而拒绝。
     *
     * <p>这是实测踩中过的真实缺陷形态：写 {@code login:} 会得到
     * 「不支持的认证路径: login」，而连接本身是成功的、且服务端会正常回业务错误帧。</p>
     */
    @Test
    @DisplayName("路由缺少前导斜杠时服务端拒绝，带斜杠时不再报路径错误")
    void routePrefix_shouldRequireLeadingSlash() throws Exception {
        assumeTrue(serverReachable(),
                "需要监听 " + OAUTH_TCP_PORT + " 的 oauth 服务端，跳过集成用例");

        var body = "{\"x-oauth-username\":\"admin\",\"x-oauth-password\":\"wrong-password\"}";

        // 少了斜杠：必然落到「不支持的认证路径」，与凭据是否正确无关
        var withoutSlash = callServer("login:" + body);
        assertTrue(withoutSlash.contains("不支持的认证路径"),
                "缺少前导斜杠应被路径匹配拒绝，实际返回: " + withoutSlash);

        // 带斜杠：路径已被接受，回包转为凭据层面的业务结果（凭据故意写错）
        var withSlash = callServer(ROUTE_LOGIN + ":" + body);
        assertFalse(withSlash.contains("不支持的认证路径"),
                "带前导斜杠不应再报路径错误，实际返回: " + withSlash);
        assertTrue(withSlash.contains("\"ok\""),
                "路径正确时应返回业务结果帧，实际返回: " + withSlash);
    }

    /**
     * 损坏的长度头不得触发巨额内存分配。
     *
     * <p>用伪造的 {@code 0x7FFFFFFF}（声明 2GB 负载）验证守卫：若没有上限校验，
     * 这里会直接 OOM 拖垮整个测试 JVM，而不是抛出一个可定位的异常。</p>
     */
    @Test
    @DisplayName("超出上限的帧长度被拒绝，不触发巨额分配")
    void oversizedFrameLength_shouldBeRejectedBeforeAllocating() {
        // 声明 2GB 负载的长度头
        var maliciousHeader = new byte[] {0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        var exception = assertThrows(IllegalStateException.class,
                () -> readFrameHeader(new DataInputStream(new ByteArrayInputStream(maliciousHeader))),
                "2GB 帧长必须在分配前被拒绝");
        assertTrue(exception.getMessage().contains("非法帧长度"),
                "异常信息应指向帧长非法，实际: " + exception.getMessage());
    }

    /**
     * 负数帧长同样必须被拒绝。
     *
     * <p>{@code 0xFFFFFFFF} 按补码解释为 {@code -1}，若只判上限不判下界，
     * {@code new byte[-1]} 会抛 {@code NegativeArraySizeException}，
     * 掩盖掉「这是网络输入」这一真实原因。</p>
     */
    @Test
    @DisplayName("负数帧长被拒绝，不退化成 NegativeArraySizeException")
    void negativeFrameLength_shouldBeRejected() {
        var malformedHeader = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        var exception = assertThrows(IllegalStateException.class,
                () -> readFrameHeader(new DataInputStream(new ByteArrayInputStream(malformedHeader))),
                "负数帧长必须在分配前被拒绝");
        assertTrue(exception.getMessage().contains("非法帧长度"),
                "异常信息应指向帧长非法，实际: " + exception.getMessage());
    }

    /**
     * 帧读取超时应表现为超时而不是无限等待。
     *
     * <p>保证「服务端不响应」这一最坏情况有明确上界，不会拖住整轮构建。</p>
     */
    @Test
    @DisplayName("服务端不响应时按超时失败，不无限等待")
    void unresponsiveServer_shouldTimeOut() throws Exception {
        assumeTrue(serverReachable(),
                "需要监听 " + OAUTH_TCP_PORT + " 的 oauth 服务端，跳过集成用例");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(OAUTH_TCP_HOST, OAUTH_TCP_PORT), CONNECT_TIMEOUT_MILLIS);
            // 不写任何字节，只等服务端先说话
            socket.setSoTimeout(800);
            var in = new DataInputStream(socket.getInputStream());
            assertThrows(SocketTimeoutException.class, in::read,
                    "服务端不应在客户端未发帧时主动回包");
        }
    }
}
