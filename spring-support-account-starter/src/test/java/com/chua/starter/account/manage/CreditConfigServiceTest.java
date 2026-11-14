// [P3C A20 豁免] A20/A30/A31: 含建表 DDL 与协议常量字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.manage;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.chua.starter.account.manage.mapper.SysAccountCreditConfigMapper;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.impl.SysAccountCreditConfigServiceImpl;
import com.chua.starter.account.manage.store.SysAccountCreditConfig;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上游余额查询配置的真实数据库 / 真实 HTTP 回归。
 *
 * <p>覆盖三件最容易坏的事：</p>
 * <ul>
 *   <li>令牌落库加密、读接口只回 {@code hasToken}；编辑留空或掩码不得覆盖原密文；</li>
 *   <li>停用的配置不参与查询（{@link SysAccountCreditConfigServiceImpl#queryOf} 返回空）；</li>
 *   <li>配置 → datasearch 的 {@code CreditQueryProvider} → 字段表达式 → 金额，
 *       这条链用本地 HTTP 服务跑通，不依赖任何外部厂商接口。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.82
 */
@DisplayName("余额查询配置：加密落库、停用不查、表达式取值全链路")
class CreditConfigServiceTest {

    /**
     * 测试密钥（AesCodec 内部做 SHA-256 派生）
     */
    private static final String SECRET_KEY = "unit-test-credit-config-key";

    /**
     * H2 连接串前缀（每个用例独立内存库）
     */
    private static final String JDBC_URL_PREFIX = "jdbc:h2:mem:account_credit_";

    /**
     * H2 连接串后缀：MySQL 兼容模式 + 库常驻
     */
    private static final String JDBC_URL_SUFFIX = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    /**
     * 建表语句（与 V6.9__add_credit_config.sql 列一一对应，去掉 MySQL 专有语法）
     */
    private static final String DDL_CREDIT_CONFIG = """
            CREATE TABLE sys_account_credit_config (
                sys_account_credit_config_provider VARCHAR(64) NOT NULL PRIMARY KEY,
                sys_account_credit_config_kind VARCHAR(32),
                sys_account_credit_config_url VARCHAR(1024),
                sys_account_credit_config_method VARCHAR(8),
                sys_account_credit_config_header VARCHAR(64),
                sys_account_credit_config_token VARCHAR(1024),
                sys_account_credit_config_ak VARCHAR(1024),
                sys_account_credit_config_sk VARCHAR(1024),
                sys_account_credit_config_expr VARCHAR(512),
                sys_account_credit_config_billing VARCHAR(16),
                sys_account_credit_config_status TINYINT NOT NULL DEFAULT 1,
                sys_account_credit_config_remark VARCHAR(512),
                sys_account_credit_config_deleted TINYINT NOT NULL DEFAULT 0,
                sys_account_credit_config_created_at BIGINT,
                sys_account_credit_config_updated_at BIGINT
            )
            """;

    /**
     * 示例上游 id
     */
    private static final String PROVIDER = "moxuna";

    /**
     * 示例字段表达式（500000 额度 = 1 USD）
     */
    private static final String EXPRESSION = "$data.user_quota.total_available / 500000";

    /**
     * 真实编解码器
     */
    private AccountSecretCodec secretCodec;

    /**
     * 真实映射器
     */
    private SysAccountCreditConfigMapper configMapper;

    /**
     * 真实服务
     */
    private SysAccountCreditConfigServiceImpl service;

    /**
     * 当前数据库会话
     */
    private SqlSession sqlSession;

    /**
     * 装配 H2 内存库与真实 Mapper / 服务。
     *
     * @throws Exception 数据库或 MyBatis 装配异常
     */
    @BeforeEach
    void setUp() throws Exception {
        String url = JDBC_URL_PREFIX + UUID.randomUUID().toString().replace("-", "") + JDBC_URL_SUFFIX;
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(DDL_CREDIT_CONFIG);
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("account-credit-test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(SysAccountCreditConfigMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        sqlSession = factory.openSession(true);
        configMapper = sqlSession.getMapper(SysAccountCreditConfigMapper.class);
        secretCodec = new AccountSecretCodec(SECRET_KEY);
        service = new SysAccountCreditConfigServiceImpl(configMapper, secretCodec);
    }

    /**
     * 关闭会话。
     */
    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    /**
     * 令牌必须加密落库，读接口只回 hasToken。
     */
    @Test
    @DisplayName("保存：令牌加密落库，读接口只回 hasToken")
    void saveEncryptsToken() {
        SysAccountCreditConfig saved = service.save(PROVIDER, config("sk-plain-token"));

        assertTrue(Boolean.TRUE.equals(saved.getHasToken()), "读接口应标记已配置令牌");
        assertNull(saved.getToken(), "读接口不得回显令牌");

        SysAccountCreditConfig stored = configMapper.selectById(PROVIDER);
        assertNotNull(stored);
        assertTrue(stored.getToken().startsWith(AccountSecretCodec.PREFIX), "落库值应为 enc: 前缀密文");
        assertFalse(stored.getToken().contains("sk-plain-token"), "落库值不应包含明文");
        assertEquals("sk-plain-token", secretCodec.decrypt(stored.getToken()));
    }

    /**
     * 编辑时留空或传掩码表示「不修改」，原密文必须保留。
     */
    @Test
    @DisplayName("编辑：留空 / 掩码不覆盖原令牌")
    void maskedTokenKeepsCipher() {
        service.save(PROVIDER, config("sk-keep-me"));
        String cipher = configMapper.selectById(PROVIDER).getToken();

        service.save(PROVIDER, config(""));
        assertEquals(cipher, configMapper.selectById(PROVIDER).getToken(), "留空不应改动令牌");

        service.save(PROVIDER, config("****e-me"));
        assertEquals(cipher, configMapper.selectById(PROVIDER).getToken(), "掩码不应改动令牌");
    }

    /**
     * 停用的配置不参与查询。
     */
    @Test
    @DisplayName("停用：queryOf 不返回请求")
    void disabledConfigIsSkipped() {
        assertNull(service.queryOf(PROVIDER), "没配置时应返回空");

        SysAccountCreditConfig input = config("sk-token");
        input.setStatus(0);
        service.save(PROVIDER, input);
        assertNull(service.queryOf(PROVIDER), "停用的配置不应被查询");

        input.setStatus(1);
        service.save(PROVIDER, input);
        CreditQuery query = service.queryOf(PROVIDER);
        assertNotNull(query, "启用的配置应能取到请求");
        assertEquals("sk-token", query.getToken(), "查询请求里的令牌应已解密");
        assertEquals(EXPRESSION, query.getExpression());
    }

    /**
     * 配置 → SPI → 表达式 → 金额，走本地 HTTP 服务验证全链路。
     *
     * @throws IOException 本地服务启动失败
     */
    @Test
    @DisplayName("试查：表达式把 2500000 额度换算成 5 USD")
    void testReadsBalanceThroughSpi() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/usage/token", this::answerUsage);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            SysAccountCreditConfig input = config("sk-live");
            input.setUrl(base + "/api/usage/token");
            input.setBilling("usd");

            CreditResult result = service.test(input);

            assertNull(result.getError(), "应查到余额，实际失败: " + result.getError());
            assertEquals(0, new BigDecimal("5").compareTo(result.getAmount()), "2500000 / 500000 应为 5");
            assertEquals("USD", result.getCurrency());
            assertEquals("expression", result.getSource());
            assertEquals(1, result.getParts().size());
        } finally {
            server.stop(0);
        }
    }

    /**
     * 删除是逻辑删除，之后 queryOf 拿不到。
     */
    @Test
    @DisplayName("删除：逻辑删除后不再查询")
    void deleteHidesConfig() {
        service.save(PROVIDER, config("sk-token"));
        assertNotNull(service.queryOf(PROVIDER));

        assertTrue(service.delete(PROVIDER));
        assertNull(service.queryOf(PROVIDER), "删除后不应再查");
        assertNull(service.get(PROVIDER), "删除后详情应为空");
    }

    /**
     * 造一条可用的配置。
     *
     * @param token 令牌明文（可空）
     * @return 配置
     */
    private static SysAccountCreditConfig config(String token) {
        SysAccountCreditConfig config = new SysAccountCreditConfig();
        config.setProviderId(PROVIDER);
        config.setKind("expression");
        config.setUrl("http://127.0.0.1:1/api/usage/token");
        config.setMethod("GET");
        config.setExpression(EXPRESSION);
        config.setBilling("usd");
        config.setToken(token);
        config.setStatus(1);
        return config;
    }

    /**
     * 本地余额接口：返回 magpie 里 moxuna 那种额度结构。
     *
     * @param exchange 请求
     * @throws IOException 写响应失败
     */
    private void answerUsage(HttpExchange exchange) throws IOException {
        byte[] body = "{\"data\":{\"user_quota\":{\"total_available\":2500000}}}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
