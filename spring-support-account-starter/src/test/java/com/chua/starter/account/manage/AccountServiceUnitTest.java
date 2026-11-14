// [P3C A20 豁免] A20/A30/A31: 含建表 DDL 与协议常量字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.manage;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.ManageAccountMapper;
import com.chua.starter.account.manage.mapper.SysAccountAppKeyMapper;
import com.chua.starter.account.manage.mapper.SysAccountCookieMapper;
import com.chua.starter.account.manage.mapper.SysAccountSecretMapper;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.impl.ManageAccountServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountAppKeyServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountCookieServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountSecretServiceImpl;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.manage.store.SysAccountAppKey;
import com.chua.starter.account.manage.store.SysAccountCookie;
import com.chua.starter.account.support.AccountUserContext;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账号 / 访问密钥 / Cookie 服务的真实数据库回归测试。
 *
 * <p>覆盖三条最容易回归的口径：</p>
 * <ul>
 *   <li>敏感值落库加密、接口只回 {@code hasSecret} / {@code hasValue}；</li>
 *   <li>编辑时回传掩码或留空代表「不修改」，原密文必须保留；</li>
 *   <li>归属隔离：个人数据仅本人可改，部署级数据（{@code owner} 为空）所有人可改。</li>
 * </ul>
 *
 * <p>P3C40 去 mockito 改造：原实现用 Mockito 打桩三个 Mapper，断言的只是
 * 「服务把参数传给了一个假对象」。现在改为 H2（MySQL 兼容模式）建三张真表，
 * 程序化装配 MyBatis-Plus 会话工厂并注册真实 Mapper，服务层与
 * {@link AccountSecretCodec} 均为真实实例；每条用例既断言接口返回值，
 * 也回查数据库验证密文、名称、归属与逻辑删除状态确实落库。</p>
 *
 * <p>身份上下文用 Spring 自带的 {@link MockHttpServletRequest} 承载请求属性
 * （spring-test 官方夹具，真实存储属性，不是 Mockito 打桩）。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("账号模块服务层：真实 H2 落库验证加密、掩码与归属隔离")
class AccountServiceUnitTest {

    /**
     * 测试密钥（任意长度，AesCodec 内部会做 SHA-256 派生）
     */
    private static final String SECRET_KEY = "unit-test-account-secret-key";

    /**
     * 过期鉴权判定的时间步长
     */
    private static final long ONE_SECOND_MS = 1000L;

    /**
     * 当前登录用户
     */
    private static final String CURRENT_USER = "user-1";

    /**
     * 他人用户
     */
    private static final String OTHER_USER = "user-2";

    /**
     * H2 连接串前缀（每个用例独立内存库，避免互相污染）
     */
    private static final String JDBC_URL_PREFIX = "jdbc:h2:mem:account_svc_";

    /**
     * H2 连接串后缀：MySQL 兼容模式 + 库常驻
     */
    private static final String JDBC_URL_SUFFIX = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    /**
     * 账号表建表语句（与 V6.5__add_account.sql 列一一对应，去掉 MySQL 专有语法）
     */
    private static final String DDL_ACCOUNT = """
            CREATE TABLE sys_account (
                sys_account_id VARCHAR(64) NOT NULL PRIMARY KEY,
                sys_account_code VARCHAR(128) NOT NULL,
                sys_account_name VARCHAR(128) NOT NULL,
                sys_account_kind VARCHAR(32) NOT NULL,
                sys_account_vendor VARCHAR(64),
                sys_account_channel_key VARCHAR(128),
                sys_account_username VARCHAR(255),
                sys_account_secret VARCHAR(1024),
                sys_account_endpoint VARCHAR(512),
                sys_account_config VARCHAR(1024),
                sys_account_owner VARCHAR(64),
                sys_account_status TINYINT NOT NULL DEFAULT 1,
                sys_account_default TINYINT NOT NULL DEFAULT 0,
                sys_account_verify_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
                sys_account_verify_at BIGINT,
                sys_account_verify_message VARCHAR(512),
                sys_account_deleted TINYINT NOT NULL DEFAULT 0,
                sys_account_created_at BIGINT,
                sys_account_updated_at BIGINT
            )
            """;

    /**
     * 访问密钥表建表语句
     */
    private static final String DDL_APP_KEY = """
            CREATE TABLE sys_account_app_key (
                sys_account_app_key_id VARCHAR(64) NOT NULL PRIMARY KEY,
                sys_account_app_key_name VARCHAR(128) NOT NULL,
                sys_account_app_key_app_key VARCHAR(128) NOT NULL,
                sys_account_app_key_secret VARCHAR(1024),
                sys_account_app_key_owner VARCHAR(64),
                sys_account_app_key_status TINYINT NOT NULL DEFAULT 1,
                sys_account_app_key_expire_at BIGINT,
                sys_account_app_key_ip_whitelist VARCHAR(512),
                sys_account_app_key_remark VARCHAR(512),
                sys_account_app_key_last_used_at BIGINT,
                sys_account_app_key_deleted TINYINT NOT NULL DEFAULT 0,
                sys_account_app_key_created_at BIGINT,
                sys_account_app_key_updated_at BIGINT
            )
            """;

    /**
     * Cookie 表建表语句
     */
    private static final String DDL_COOKIE = """
            CREATE TABLE sys_account_cookie (
                sys_account_cookie_id VARCHAR(64) NOT NULL PRIMARY KEY,
                sys_account_cookie_name VARCHAR(128) NOT NULL,
                sys_account_cookie_domain VARCHAR(255),
                sys_account_cookie_path VARCHAR(255) DEFAULT '/',
                sys_account_cookie_value VARCHAR(2048),
                sys_account_cookie_owner VARCHAR(64),
                sys_account_cookie_status TINYINT NOT NULL DEFAULT 1,
                sys_account_cookie_expire_at BIGINT,
                sys_account_cookie_source VARCHAR(64),
                sys_account_cookie_remark VARCHAR(512),
                sys_account_cookie_deleted TINYINT NOT NULL DEFAULT 0,
                sys_account_cookie_created_at BIGINT,
                sys_account_cookie_updated_at BIGINT
            )
            """;

    /**
     * 密钥表建表语句（与 V6.6__add_account_secret.sql 列一一对应）
     */
    private static final String DDL_SECRET = """
            CREATE TABLE sys_account_secret (
                sys_account_secret_id VARCHAR(64) NOT NULL PRIMARY KEY,
                sys_account_secret_name VARCHAR(128) NOT NULL,
                sys_account_secret_provider VARCHAR(64),
                sys_account_secret_type VARCHAR(128),
                sys_account_secret_access_key VARCHAR(255),
                sys_account_secret_secret VARCHAR(2048),
                sys_account_secret_region VARCHAR(64),
                sys_account_secret_endpoint VARCHAR(512),
                sys_account_secret_owner VARCHAR(64),
                sys_account_secret_status TINYINT NOT NULL DEFAULT 1,
                sys_account_secret_remark VARCHAR(512),
                sys_account_secret_deleted TINYINT NOT NULL DEFAULT 0,
                sys_account_secret_created_at BIGINT,
                sys_account_secret_updated_at BIGINT
            )
            """;

    /**
     * 真实编解码器（AES-GCM，验证加密往返）
     */
    private AccountSecretCodec secretCodec;

    /**
     * 真实账号映射器
     */
    private ManageAccountMapper accountMapper;

    /**
     * 真实访问密钥映射器
     */
    private SysAccountAppKeyMapper appKeyMapper;

    /**
     * 真实 Cookie 映射器
     */
    private SysAccountCookieMapper cookieMapper;

    /**
     * 真实密钥映射器
     */
    private SysAccountSecretMapper secretMapper;

    /**
     * 真实账号服务
     */
    private ManageAccountServiceImpl accountService;

    /**
     * 真实密钥库服务（账号创建时按 secretId 取用密钥）
     */
    private SysAccountSecretServiceImpl secretService;

    /**
     * 真实访问密钥服务
     */
    private SysAccountAppKeyServiceImpl appKeyService;

    /**
     * 真实 Cookie 服务
     */
    private SysAccountCookieServiceImpl cookieService;

    /**
     * 当前测试的数据库会话
     */
    private SqlSession sqlSession;

    /**
     * 建立独立的 H2 内存库、回放建表、装配真实会话工厂与三个服务。
     *
     * @throws Exception 数据库或 MyBatis 装配异常
     */
    @BeforeEach
    void setUp() throws Exception {
        String url = JDBC_URL_PREFIX + UUID.randomUUID().toString().replace("-", "") + JDBC_URL_SUFFIX;
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(DDL_ACCOUNT);
            statement.execute(DDL_APP_KEY);
            statement.execute(DDL_COOKIE);
            statement.execute(DDL_SECRET);
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("account-test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ManageAccountMapper.class);
        configuration.addMapper(SysAccountAppKeyMapper.class);
        configuration.addMapper(SysAccountCookieMapper.class);
        configuration.addMapper(SysAccountSecretMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        sqlSession = factory.openSession(true);
        accountMapper = sqlSession.getMapper(ManageAccountMapper.class);
        appKeyMapper = sqlSession.getMapper(SysAccountAppKeyMapper.class);
        cookieMapper = sqlSession.getMapper(SysAccountCookieMapper.class);
        secretMapper = sqlSession.getMapper(SysAccountSecretMapper.class);
        secretCodec = new AccountSecretCodec(SECRET_KEY);
        secretService = new SysAccountSecretServiceImpl(secretMapper, secretCodec);
        accountService = new ManageAccountServiceImpl(accountMapper, secretCodec, secretService);
        appKeyService = new SysAccountAppKeyServiceImpl(appKeyMapper, secretCodec);
        cookieService = new SysAccountCookieServiceImpl(cookieMapper, secretCodec);
    }

    /**
     * 释放会话并清理请求上下文。
     */
    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 模拟已登录请求：身份来自请求属性 {@code userId}。
     *
     * @param userId 用户标识
     */
    private void loginAs(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AccountUserContext.USER_ID_ATTRIBUTE, userId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * 构造一个可入库的账号请求体。
     *
     * @return 账号
     */
    private SysAccount newAccountInput() {
        SysAccount account = new SysAccount();
        account.setCode("dev-key-1");
        account.setName("设备密钥");
        account.setKind(AccountKind.API_KEY);
        account.setVendor("vendor-a");
        account.setUsername("device-01");
        account.setSecret("plain-secret");
        account.setConfig(Map.of("region", "cn"));
        return account;
    }

    /**
     * 直接向真库插入一条账号记录（用于预置更新 / 查询场景的数据）。
     *
     * @param accountId 账号标识
     * @param code      账号编码
     * @param name      账号名称
     * @param owner     归属用户，null 表示部署级共享
     * @param secret    落库密文
     * @return 已落库的账号
     */
    private SysAccount seedAccount(String accountId, String code, String name, String owner, String secret) {
        SysAccount account = new SysAccount();
        account.setAccountId(accountId);
        account.setCode(code);
        account.setName(name);
        account.setKind(AccountKind.API_KEY);
        account.setStatus(1);
        account.setVerifyStatus("UNKNOWN");
        account.setOwner(owner);
        account.setSecret(secret);
        account.setDeleted(0);
        account.setCreatedAt(System.currentTimeMillis());
        account.setUpdatedAt(System.currentTimeMillis());
        accountMapper.insert(account);
        return account;
    }

    /**
     * 直接向真库插入一条访问密钥记录。
     *
     * @param appKeyId 密钥标识
     * @param appKey   访问 Key
     * @param secret   落库密文
     * @return 已落库的访问密钥
     */
    private SysAccountAppKey seedAppKey(String appKeyId, String appKey, String secret) {
        SysAccountAppKey key = new SysAccountAppKey();
        key.setAppKeyId(appKeyId);
        key.setAppKey(appKey);
        key.setName("外部集成");
        key.setSecret(secret);
        key.setStatus(1);
        key.setIpWhitelist("10.0.*");
        key.setDeleted(0);
        key.setCreatedAt(System.currentTimeMillis());
        key.setUpdatedAt(System.currentTimeMillis());
        appKeyMapper.insert(key);
        return key;
    }

    /**
     * 直接向真库插入一条 Cookie 记录。
     *
     * @param cookieId cookie 标识
     * @param name     名称
     * @param owner    归属用户
     * @param value    落库密文
     * @return 已落库的 Cookie
     */
    private SysAccountCookie seedCookie(String cookieId, String name, String owner, String value) {
        SysAccountCookie cookie = new SysAccountCookie();
        cookie.setCookieId(cookieId);
        cookie.setName(name);
        cookie.setCookieValue(value);
        cookie.setStatus(1);
        cookie.setOwner(owner);
        cookie.setDeleted(0);
        cookie.setCreatedAt(System.currentTimeMillis());
        cookie.setUpdatedAt(System.currentTimeMillis());
        cookieMapper.insert(cookie);
        return cookie;
    }

    /**
     * 新增账号：凭据加密落库，回参不含明文。
     */
    @Test
    @DisplayName("新增账号：凭据加密落库，回参不含明文")
    void createEncryptsSecretAndMasksResponse() {
        SysAccount saved = accountService.create(newAccountInput());
        SysAccount stored = accountMapper.selectById(saved.getAccountId());

        assertNotNull(stored);
        assertTrue(stored.getSecret().startsWith(AccountSecretCodec.PREFIX));
        assertEquals("plain-secret", secretCodec.decrypt(stored.getSecret()));
        assertNull(saved.getSecret());
        assertTrue(saved.getHasSecret());
        assertEquals("UNKNOWN", saved.getVerifyStatus());
        assertEquals(1, saved.getStatus());
    }

    /**
     * 新增账号：编码重复直接冲突。
     */
    @Test
    @DisplayName("新增账号：编码重复直接冲突")
    void createDuplicateCodeRejected() {
        seedAccount("acc-existing", "dev-key-1", "已存在密钥", null, secretCodec.encrypt("old"));

        ApiException error = assertThrows(ApiException.class, () -> accountService.create(newAccountInput()));

        assertEquals(ApiErrorCode.CONFLICT, error.getErrorCode());
        assertEquals(1, accountMapper.selectCount(null));
    }

    /**
     * 更新账号：回传掩码表示不修改凭据。
     */
    @Test
    @DisplayName("更新账号：回传掩码表示不修改凭据")
    void updateMaskedSecretKeepsCipher() {
        seedAccount("acc-1", "dev-key-1", "设备密钥", null, secretCodec.encrypt("old-secret"));

        SysAccount input = new SysAccount();
        input.setAccountId("acc-1");
        input.setCode("dev-key-1");
        input.setName("设备密钥-改名");
        input.setKind(AccountKind.API_KEY);
        input.setSecret("ol****et");
        accountService.update(input);

        SysAccount stored = accountMapper.selectById("acc-1");
        assertNotNull(stored);
        assertEquals("old-secret", secretCodec.decrypt(stored.getSecret()));
        assertEquals("设备密钥-改名", stored.getName());
    }

    /**
     * 更新账号：他人个人账号抛 403，且库内数据未被改动。
     */
    @Test
    @DisplayName("更新账号：他人个人账号抛 403")
    void updateForeignPersonalAccountForbidden() {
        loginAs(CURRENT_USER);
        seedAccount("acc-2", "dev-key-2", "他人密钥", OTHER_USER, secretCodec.encrypt("foreign"));

        SysAccount input = new SysAccount();
        input.setAccountId("acc-2");
        input.setCode("dev-key-2");
        input.setName("越权改名");
        input.setKind(AccountKind.API_KEY);

        ApiException error = assertThrows(ApiException.class, () -> accountService.update(input));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals("他人密钥", accountMapper.selectById("acc-2").getName());
    }

    /**
     * 查询账号详情：他人个人账号抛 403。
     */
    @Test
    @DisplayName("查询账号详情：他人个人账号抛 403")
    void getForeignPersonalAccountForbidden() {
        loginAs(CURRENT_USER);
        seedAccount("acc-2", "dev-key-2", "他人密钥", OTHER_USER, secretCodec.encrypt("foreign"));

        ApiException error = assertThrows(ApiException.class, () -> accountService.get("acc-2"));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
    }

    /**
     * 查询账号详情：本人账号可读，凭据只标已配置。
     */
    @Test
    @DisplayName("查询账号详情：本人账号可读，凭据只标已配置")
    void getOwnPersonalAccountMasked() {
        loginAs(CURRENT_USER);
        seedAccount("acc-3", "dev-key-3", "我的密钥", CURRENT_USER, secretCodec.encrypt("mine"));

        SysAccount result = accountService.get("acc-3");

        assertEquals("acc-3", result.getAccountId());
        assertNull(result.getSecret());
        assertTrue(result.getHasSecret());
    }

    /**
     * 查询账号：列表不回显密文，只标记已配置。
     */
    @Test
    @DisplayName("查询账号：列表不回显密文，只标记已配置")
    void listHidesSecret() {
        seedAccount("acc-3", "dev-key-3", "部署密钥", null, secretCodec.encrypt("hidden"));

        SysAccount result = accountService.list(null, null, null).get(0);

        assertNull(result.getSecret());
        assertTrue(result.getHasSecret());
        assertNotNull(accountMapper.selectById("acc-3").getSecret());
    }

    /**
     * 新增访问密钥：明文只返回一次，落库为密文。
     */
    @Test
    @DisplayName("新增访问密钥：明文只返回一次，落库为密文")
    void appKeyCreateReturnsPlainSecretOnce() {
        SysAccountAppKey input = new SysAccountAppKey();
        input.setName("外部集成");

        SysAccountAppKey created = appKeyService.create(input);

        SysAccountAppKey stored = appKeyMapper.selectById(created.getAppKeyId());
        assertNotNull(stored);
        assertTrue(created.getPlainSecret().startsWith("sk_"));
        assertTrue(created.getAppKey().startsWith("ak_"));
        assertNull(created.getSecret());
        assertEquals(created.getPlainSecret(), secretCodec.decrypt(stored.getSecret()));
    }

    /**
     * 轮换访问密钥：旧 Secret 失效、新明文只返回一次。
     */
    @Test
    @DisplayName("轮换访问密钥：旧 Secret 失效、新明文只返回一次")
    void appKeyRotateChangesSecret() {
        seedAppKey("key-1", "ak_old", secretCodec.encrypt("sk_old"));

        SysAccountAppKey rotated = appKeyService.rotate("key-1");

        assertNotEquals("sk_old", rotated.getPlainSecret());
        assertNull(rotated.getSecret());
        assertEquals(rotated.getPlainSecret(),
                secretCodec.decrypt(appKeyMapper.selectById("key-1").getSecret()));
    }

    /**
     * 访问密钥鉴权：过期、停用、IP 白名单、Secret 不匹配均拒绝。
     */
    @Test
    @DisplayName("访问密钥鉴权：过期、停用、IP 白名单、Secret 不匹配均拒绝")
    void appKeyAuthenticateRejectsInvalid() {
        SysAccountAppKey key = seedAppKey("key-2", "ak_2", secretCodec.encrypt("sk_2"));

        assertFalse(appKeyService.authenticate("ak_2", "sk_wrong", "10.0.0.1"));
        assertFalse(appKeyService.authenticate("ak_2", "sk_2", "192.168.0.1"));
        assertTrue(appKeyService.authenticate("ak_2", "sk_2", "10.0.0.1"));

        key.setStatus(0);
        assertEquals(1, appKeyMapper.updateById(key));
        assertFalse(appKeyService.authenticate("ak_2", "sk_2", "10.0.0.1"));

        key.setStatus(1);
        key.setExpireAt(System.currentTimeMillis() - ONE_SECOND_MS);
        assertEquals(1, appKeyMapper.updateById(key));
        assertFalse(appKeyService.authenticate("ak_2", "sk_2", "10.0.0.1"));
    }

    /**
     * 新增 Cookie：内容加密、回参只标记是否已配置。
     */
    @Test
    @DisplayName("新增 Cookie：内容加密、回参只标记是否已配置")
    void cookieCreateEncryptsAndMasks() {
        SysAccountCookie input = new SysAccountCookie();
        input.setName("站点 Cookie");
        input.setDomain("example.com");
        input.setCookieValue("session=abc");

        SysAccountCookie created = cookieService.create(input);

        SysAccountCookie stored = cookieMapper.selectById(created.getCookieId());
        assertNotNull(stored);
        assertNull(created.getCookieValue());
        assertTrue(created.getHasValue());
        assertEquals("/", created.getPath());
        assertEquals("session=abc", secretCodec.decrypt(stored.getCookieValue()));
    }

    /**
     * 更新 Cookie：掩码内容不覆盖原值。
     */
    @Test
    @DisplayName("更新 Cookie：掩码内容不覆盖原值")
    void cookieUpdateMaskedValueKeepsCipher() {
        seedCookie("cookie-1", "站点 Cookie", null, secretCodec.encrypt("session=abc"));

        SysAccountCookie input = new SysAccountCookie();
        input.setCookieId("cookie-1");
        input.setName("站点 Cookie-改名");
        input.setCookieValue("se****bc");
        cookieService.update(input);

        SysAccountCookie stored = cookieMapper.selectById("cookie-1");
        assertNotNull(stored);
        assertEquals("session=abc", secretCodec.decrypt(stored.getCookieValue()));
        assertEquals("站点 Cookie-改名", stored.getName());
    }

    /**
     * 查询 Cookie 详情：他人个人 Cookie 抛 403。
     */
    @Test
    @DisplayName("查询 Cookie 详情：他人个人 Cookie 抛 403")
    void cookieGetForeignPersonalForbidden() {
        loginAs(CURRENT_USER);
        seedCookie("cookie-3", "他人 Cookie", OTHER_USER, secretCodec.encrypt("session=xyz"));

        ApiException error = assertThrows(ApiException.class, () -> cookieService.get("cookie-3"));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
    }

    /**
     * 删除 Cookie：他人个人 Cookie 抛 403，且记录仍在库中。
     */
    @Test
    @DisplayName("删除 Cookie：他人个人 Cookie 抛 403")
    void cookieDeleteForeignPersonalForbidden() {
        loginAs(CURRENT_USER);
        seedCookie("cookie-2", "他人 Cookie", OTHER_USER, secretCodec.encrypt("session=xyz"));

        ApiException error = assertThrows(ApiException.class, () -> cookieService.delete("cookie-2"));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
        assertNotNull(cookieMapper.selectById("cookie-2"));
    }

    /**
     * 新增账号：未注册的种类直接拒绝（种类白名单来自 SPI，不再写死三个常量）。
     */
    @Test
    @DisplayName("新增账号：未注册的种类直接拒绝")
    void createRejectsUnknownKind() {
        SysAccount input = newAccountInput();
        input.setKind("UNKNOWN_KIND");

        ApiException error = assertThrows(ApiException.class, () -> accountService.create(input));

        assertEquals(ApiErrorCode.INVALID_PARAM, error.getErrorCode());
        assertEquals(0, accountMapper.selectCount(null), "非法种类不应落库");
    }

    /**
     * 新增账号：种类大小写不敏感，落库统一大写。
     */
    @Test
    @DisplayName("新增账号：种类落库统一大写")
    void createNormalizesKindCase() {
        SysAccount input = newAccountInput();
        input.setKind(" email ");

        SysAccount saved = accountService.create(input);

        assertEquals(AccountKind.EMAIL, accountMapper.selectById(saved.getAccountId()).getKind());
    }
}
