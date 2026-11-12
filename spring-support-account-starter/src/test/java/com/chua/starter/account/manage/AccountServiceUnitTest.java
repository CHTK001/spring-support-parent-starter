package com.chua.starter.account.manage;

import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.manage.mapper.SysAccountAppKeyMapper;
import com.chua.starter.account.manage.mapper.SysAccountCookieMapper;
import com.chua.starter.account.manage.mapper.ManageAccountMapper;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.impl.SysAccountAppKeyServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountCookieServiceImpl;
import com.chua.starter.account.manage.service.impl.ManageAccountServiceImpl;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.manage.store.SysAccountAppKey;
import com.chua.starter.account.manage.store.SysAccountCookie;
import com.chua.starter.account.support.AccountUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号 / 访问密钥 / Cookie 服务的纯净单测。
 *
 * <p>只覆盖三条最容易回归的口径：</p>
 * <ul>
 *   <li>敏感值落库加密、接口只回 {@code hasSecret} / {@code hasValue}；</li>
 *   <li>编辑时回传掩码或留空代表「不修改」，原密文必须保留；</li>
 *   <li>归属隔离：个人数据仅本人可改，部署级数据（{@code owner} 为空）所有人可改。</li>
 * </ul>
 *
 * <p>不启动 Spring 容器，Mapper 与编解码器均用 Mockito / 真实实例替代。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("账号模块服务层：加密、掩码与归属隔离")
class AccountServiceUnitTest {

    /**
     * 测试密钥（任意长度，AesCodec 内部会做 SHA-256 派生）
     */
    private static final String SECRET_KEY = "unit-test-account-secret-key";
    /** 过期鉴权判定的时间步长。 */
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
     * 真实编解码器：验证加密往返，不打桩
     */
    private AccountSecretCodec secretCodec;

    /**
     * 账号映射器
     */
    private ManageAccountMapper accountMapper;

    /**
     * 访问密钥映射器
     */
    private SysAccountAppKeyMapper appKeyMapper;

    /**
     * Cookie 映射器
     */
    private SysAccountCookieMapper cookieMapper;

    /**
     * 账号服务
     */
    private ManageAccountServiceImpl accountService;

    /**
     * 访问密钥服务
     */
    private SysAccountAppKeyServiceImpl appKeyService;

    /**
     * Cookie 服务
     */
    private SysAccountCookieServiceImpl cookieService;

    @BeforeEach
    void setUp() {
        secretCodec = new AccountSecretCodec(SECRET_KEY);
        accountMapper = mock(ManageAccountMapper.class);
        appKeyMapper = mock(SysAccountAppKeyMapper.class);
        cookieMapper = mock(SysAccountCookieMapper.class);
        accountService = new ManageAccountServiceImpl(accountMapper, secretCodec);
        appKeyService = new SysAccountAppKeyServiceImpl(appKeyMapper, secretCodec);
        cookieService = new SysAccountCookieServiceImpl(cookieMapper, secretCodec);
    }

    @AfterEach
    void tearDown() {
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
        account.setConfig(java.util.Map.of("region", "cn"));
        return account;
    }

    @Test
    @DisplayName("新增账号：凭据加密落库，回参不含明文")
    void createEncryptsSecretAndMasksResponse() {
        when(accountMapper.selectCount(any())).thenReturn(0L);
        // 服务在 insert 之后才清空敏感字段，必须在调用瞬间抓取落库值
        String[] storedSecret = new String[1];
        doAnswer(invocation -> {
            storedSecret[0] = ((SysAccount) invocation.getArgument(0)).getSecret();
            return 1;
        }).when(accountMapper).insert(any(SysAccount.class));

        SysAccount saved = accountService.create(newAccountInput());

        assertTrue(storedSecret[0].startsWith(AccountSecretCodec.PREFIX));
        assertEquals("plain-secret", secretCodec.decrypt(storedSecret[0]));
        assertNull(saved.getSecret());
        assertTrue(saved.getHasSecret());
        assertEquals("UNKNOWN", saved.getVerifyStatus());
        assertEquals(1, saved.getStatus());
    }

    @Test
    @DisplayName("新增账号：编码重复直接冲突")
    void createDuplicateCodeRejected() {
        when(accountMapper.selectCount(any())).thenReturn(1L);

        ApiException error = assertThrows(ApiException.class, () -> accountService.create(newAccountInput()));

        assertEquals(ApiErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    @DisplayName("更新账号：回传掩码表示不修改凭据")
    void updateMaskedSecretKeepsCipher() {
        SysAccount existing = new SysAccount();
        existing.setAccountId("acc-1");
        existing.setCode("dev-key-1");
        existing.setName("设备密钥");
        existing.setKind(AccountKind.API_KEY);
        existing.setSecret(secretCodec.encrypt("old-secret"));
        existing.setStatus(1);
        when(accountMapper.selectById("acc-1")).thenReturn(existing);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        String[] storedSecret = new String[1];
        String[] storedName = new String[1];
        doAnswer(invocation -> {
            SysAccount arg = invocation.getArgument(0);
            storedSecret[0] = arg.getSecret();
            storedName[0] = arg.getName();
            return 1;
        }).when(accountMapper).updateById(any(SysAccount.class));

        SysAccount input = new SysAccount();
        input.setAccountId("acc-1");
        input.setCode("dev-key-1");
        input.setName("设备密钥-改名");
        input.setKind(AccountKind.API_KEY);
        input.setSecret("ol****et");

        accountService.update(input);

        assertEquals(secretCodec.encrypt("old-secret"), storedSecret[0]);
        assertEquals("设备密钥-改名", storedName[0]);
    }

    @Test
    @DisplayName("更新账号：他人个人账号抛 403")
    void updateForeignPersonalAccountForbidden() {
        loginAs(CURRENT_USER);
        SysAccount existing = new SysAccount();
        existing.setAccountId("acc-2");
        existing.setCode("dev-key-2");
        existing.setName("他人密钥");
        existing.setKind(AccountKind.API_KEY);
        existing.setOwner(OTHER_USER);
        when(accountMapper.selectById("acc-2")).thenReturn(existing);

        SysAccount input = new SysAccount();
        input.setAccountId("acc-2");
        input.setCode("dev-key-2");
        input.setName("越权改名");
        input.setKind(AccountKind.API_KEY);

        ApiException error = assertThrows(ApiException.class, () -> accountService.update(input));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
        verify(accountMapper, times(0)).updateById(any(SysAccount.class));
    }

    @Test
    @DisplayName("查询账号详情：他人个人账号抛 403")
    void getForeignPersonalAccountForbidden() {
        loginAs(CURRENT_USER);
        SysAccount existing = new SysAccount();
        existing.setAccountId("acc-2");
        existing.setCode("dev-key-2");
        existing.setName("他人密钥");
        existing.setKind(AccountKind.API_KEY);
        existing.setOwner(OTHER_USER);
        when(accountMapper.selectById("acc-2")).thenReturn(existing);

        ApiException error = assertThrows(ApiException.class, () -> accountService.get("acc-2"));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    @DisplayName("查询账号详情：本人账号可读，凭据只标已配置")
    void getOwnPersonalAccountMasked() {
        loginAs(CURRENT_USER);
        SysAccount existing = new SysAccount();
        existing.setAccountId("acc-3");
        existing.setCode("dev-key-3");
        existing.setName("我的密钥");
        existing.setKind(AccountKind.API_KEY);
        existing.setOwner(CURRENT_USER);
        existing.setSecret(secretCodec.encrypt("mine"));
        when(accountMapper.selectById("acc-3")).thenReturn(existing);

        SysAccount result = accountService.get("acc-3");

        assertEquals("acc-3", result.getAccountId());
        assertNull(result.getSecret());
        assertTrue(result.getHasSecret());
    }

    @Test
    @DisplayName("查询账号：列表不回显密文，只标记已配置")
    void listHidesSecret() {
        SysAccount row = new SysAccount();
        row.setAccountId("acc-3");
        row.setCode("dev-key-3");
        row.setName("部署密钥");
        row.setKind(AccountKind.API_KEY);
        row.setSecret(secretCodec.encrypt("hidden"));
        when(accountMapper.selectList(any())).thenReturn(java.util.List.of(row));

        SysAccount result = accountService.list(null, null, null).get(0);

        assertNull(result.getSecret());
        assertTrue(result.getHasSecret());
    }

    @Test
    @DisplayName("新增访问密钥：明文只返回一次，落库为密文")
    void appKeyCreateReturnsPlainSecretOnce() {
        when(appKeyMapper.selectCount(any())).thenReturn(0L);
        String[] storedSecret = new String[1];
        doAnswer(invocation -> {
            storedSecret[0] = ((SysAccountAppKey) invocation.getArgument(0)).getSecret();
            return 1;
        }).when(appKeyMapper).insert(any(SysAccountAppKey.class));
        SysAccountAppKey input = new SysAccountAppKey();
        input.setName("外部集成");

        SysAccountAppKey created = appKeyService.create(input);

        assertTrue(created.getPlainSecret().startsWith("sk_"));
        assertTrue(created.getAppKey().startsWith("ak_"));
        assertNull(created.getSecret());
        assertEquals(created.getPlainSecret(), secretCodec.decrypt(storedSecret[0]));
    }

    @Test
    @DisplayName("轮换访问密钥：旧 Secret 失效、新明文只返回一次")
    void appKeyRotateChangesSecret() {
        SysAccountAppKey existing = new SysAccountAppKey();
        existing.setAppKeyId("key-1");
        existing.setAppKey("ak_old");
        existing.setName("外部集成");
        existing.setStatus(1);
        existing.setSecret(secretCodec.encrypt("sk_old"));
        when(appKeyMapper.selectById("key-1")).thenReturn(existing);
        String[] storedSecret = new String[1];
        doAnswer(invocation -> {
            storedSecret[0] = ((SysAccountAppKey) invocation.getArgument(0)).getSecret();
            return 1;
        }).when(appKeyMapper).updateById(any(SysAccountAppKey.class));

        SysAccountAppKey rotated = appKeyService.rotate("key-1");

        assertNotEquals("sk_old", rotated.getPlainSecret());
        assertNull(rotated.getSecret());
        assertEquals(rotated.getPlainSecret(), secretCodec.decrypt(storedSecret[0]));
    }

    @Test
    @DisplayName("访问密钥鉴权：过期、停用、IP 白名单、Secret 不匹配均拒绝")
    void appKeyAuthenticateRejectsInvalid() {
        SysAccountAppKey key = new SysAccountAppKey();
        key.setAppKeyId("key-2");
        key.setAppKey("ak_2");
        key.setStatus(1);
        key.setSecret(secretCodec.encrypt("sk_2"));
        key.setIpWhitelist("10.0.*");
        when(appKeyMapper.selectOne(any())).thenReturn(key);

        assertFalse(appKeyService.authenticate("ak_2", "sk_wrong", "10.0.0.1"));
        assertFalse(appKeyService.authenticate("ak_2", "sk_2", "192.168.0.1"));
        assertTrue(appKeyService.authenticate("ak_2", "sk_2", "10.0.0.1"));

        key.setStatus(0);
        assertFalse(appKeyService.authenticate("ak_2", "sk_2", "10.0.0.1"));

        key.setStatus(1);
        key.setExpireAt(System.currentTimeMillis() - ONE_SECOND_MS);
        assertFalse(appKeyService.authenticate("ak_2", "sk_2", "10.0.0.1"));
    }

    @Test
    @DisplayName("新增 Cookie：内容加密、回参只标记是否已配置")
    void cookieCreateEncryptsAndMasks() {
        SysAccountCookie input = new SysAccountCookie();
        input.setName("站点 Cookie");
        input.setDomain("example.com");
        input.setCookieValue("session=abc");
        String[] storedValue = new String[1];
        doAnswer(invocation -> {
            storedValue[0] = ((SysAccountCookie) invocation.getArgument(0)).getCookieValue();
            return 1;
        }).when(cookieMapper).insert(any(SysAccountCookie.class));

        SysAccountCookie created = cookieService.create(input);

        assertNull(created.getCookieValue());
        assertTrue(created.getHasValue());
        assertEquals("/", created.getPath());
        assertEquals("session=abc", secretCodec.decrypt(storedValue[0]));
    }

    @Test
    @DisplayName("更新 Cookie：掩码内容不覆盖原值")
    void cookieUpdateMaskedValueKeepsCipher() {
        SysAccountCookie existing = new SysAccountCookie();
        existing.setCookieId("cookie-1");
        existing.setName("站点 Cookie");
        existing.setCookieValue(secretCodec.encrypt("session=abc"));
        existing.setStatus(1);
        when(cookieMapper.selectById("cookie-1")).thenReturn(existing);
        String[] storedValue = new String[1];
        String[] storedName = new String[1];
        doAnswer(invocation -> {
            SysAccountCookie arg = invocation.getArgument(0);
            storedValue[0] = arg.getCookieValue();
            storedName[0] = arg.getName();
            return 1;
        }).when(cookieMapper).updateById(any(SysAccountCookie.class));

        SysAccountCookie input = new SysAccountCookie();
        input.setCookieId("cookie-1");
        input.setName("站点 Cookie-改名");
        input.setCookieValue("se****bc");

        cookieService.update(input);

        assertEquals(secretCodec.encrypt("session=abc"), storedValue[0]);
        assertEquals("站点 Cookie-改名", storedName[0]);
    }

    @Test
    @DisplayName("查询 Cookie 详情：他人个人 Cookie 抛 403")
    void cookieGetForeignPersonalForbidden() {
        loginAs(CURRENT_USER);
        SysAccountCookie existing = new SysAccountCookie();
        existing.setCookieId("cookie-3");
        existing.setName("他人 Cookie");
        existing.setOwner(OTHER_USER);
        existing.setCookieValue(secretCodec.encrypt("session=xyz"));
        when(cookieMapper.selectById("cookie-3")).thenReturn(existing);

        ApiException error = assertThrows(ApiException.class, () -> cookieService.get("cookie-3"));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    @DisplayName("删除 Cookie：他人个人 Cookie 抛 403")
    void cookieDeleteForeignPersonalForbidden() {
        loginAs(CURRENT_USER);
        SysAccountCookie existing = new SysAccountCookie();
        existing.setCookieId("cookie-2");
        existing.setName("他人 Cookie");
        existing.setOwner(OTHER_USER);
        when(cookieMapper.selectById("cookie-2")).thenReturn(existing);

        ApiException error = assertThrows(ApiException.class, () -> cookieService.delete("cookie-2"));

        assertEquals(ApiErrorCode.FORBIDDEN, error.getErrorCode());
        verify(cookieMapper, times(0)).deleteById("cookie-2");
    }
}
