package com.chua.starter.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 真实端口 E2E 测试：{@code RANDOM_PORT} + {@link RestTemplate} 走**真实 HTTP 栈**
 * （嵌入式 Tomcat 监听随机端口），区别于 MockMvc 的模拟 Servlet 容器。
 *
 * <p>覆盖：多数据源接口、api 版本路由、接口权限（本地联调模式——无会话用户注入本地 admin 放行）。</p>
 *
 * <p>注：Boot 4.1 中 {@code @LocalServerPort} 位于 {@code org.springframework.boot.test.web.server}；
 * {@code TestRestTemplate} 已从测试构件移除，改用 spring-web 的 {@link RestTemplate}。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealPortE2ETest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @BeforeEach
    void seed() throws Exception {
        seed("jdbc:h2:mem:ds1;DB_CLOSE_DELAY=-1", "ds1-data");
        seed("jdbc:h2:mem:ds2;DB_CLOSE_DELAY=-1", "ds2-data");
        seed("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL", "master-data");
    }

    private static void seed(String url, String data) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists region");
            statement.execute("create table region(data varchar(20))");
            statement.execute("insert into region values('" + data + "')");
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** 真实 HTTP：多数据源 Service 级 @DS 路由 */
    @Test
    void dsRoutingOverRealHttp() {
        Map<?, ?> ds1 = restTemplate.getForObject(url("/api/ds/region/ds1"), Map.class);
        assertNotNull(ds1, "响应不应为空");
        assertEquals("ds1-data", ds1.get("data"));
        Map<?, ?> ds2 = restTemplate.getForObject(url("/api/ds/region/ds2"), Map.class);
        assertEquals("ds2-data", ds2.get("data"));
    }

    /** 真实 HTTP：api 版本路由（查询参数） */
    @Test
    void apiVersionRoutingOverRealHttp() {
        Map<?, ?> v1 = restTemplate.getForObject(url("/api/api-version/greet?apiVersion=1"), Map.class);
        assertEquals("1", v1.get("version"));
        Map<?, ?> v2 = restTemplate.getForObject(url("/api/api-version/greet?apiVersion=2"), Map.class);
        assertEquals("2", v2.get("version"));
    }

    /** 真实 HTTP：接口权限本地联调模式（无会话用户 -> 注入本地 admin 放行） */
    @Test
    void permissionLocalDebugOverRealHttp() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/permission/query"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "本地联调模式无用户上下文应注入 admin 放行");
    }
}
