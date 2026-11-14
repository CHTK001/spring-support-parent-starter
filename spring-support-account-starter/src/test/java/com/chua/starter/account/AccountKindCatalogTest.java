package com.chua.starter.account;

import com.chua.starter.account.manage.controller.AccountController;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.AccountCapability;
import com.chua.starter.account.spi.AccountField;
import com.chua.starter.account.spi.AccountKindProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 账号种类注册表门面回归测试。
 *
 * <p>覆盖三条最容易回归的口径：</p>
 * <ul>
 *   <li><b>去重</b>：SPI 框架会给每个实现额外登记一条类简名 definition，
 *       门面必须按 {@code kind()} 去重，否则 {@code /api/accounts/kinds} 会重复；</li>
 *   <li><b>未知种类拒绝</b>：未注册的种类不得被判为支持，密钥类型回落到 {@code api}；</li>
 *   <li><b>能力位门禁</b>：只声明真有实现的种类，没装校验 / 收信实现的种类不得标为支持。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.61
 */
@DisplayName("账号种类门面：去重、未知种类拒绝与能力位门禁")
class AccountKindCatalogTest {

    /**
     * 内置种类个数（API 密钥 / 邮箱 / 短信）。
     */
    private static final int BUILTIN_KIND_COUNT = 3;

    /**
     * 验证 SPI 注册已去重，且键与实现声明一致。
     */
    @Test
    @DisplayName("种类注册表按 kind() 去重，内置三种各一条")
    void providersAreDeduplicated() {
        Map<String, AccountKindProvider> providers = AccountKindCatalog.providers();

        Assertions.assertEquals(BUILTIN_KIND_COUNT, providers.size(), "种类去重后应只剩内置三种: " + providers.keySet());
        providers.forEach((key, provider) ->
                Assertions.assertEquals(key, provider.kind(), "注册表键与实现声明不一致: " + key));
        Assertions.assertTrue(providers.containsKey(AccountKind.API_KEY));
        Assertions.assertTrue(providers.containsKey(AccountKind.EMAIL));
        Assertions.assertTrue(providers.containsKey(AccountKind.SMS));
    }

    /**
     * 验证种类查询大小写不敏感，排序稳定。
     */
    @Test
    @DisplayName("种类查询大小写不敏感，按 order 升序")
    void lookupIsCaseInsensitiveAndOrdered() {
        Assertions.assertNotNull(AccountKindCatalog.of("email"), "小写种类应能查到");
        Assertions.assertNotNull(AccountKindCatalog.of(" Email "), "带空白种类应能查到");
        Assertions.assertNull(AccountKindCatalog.of(" "), "空白种类不应命中");

        List<AccountKindProvider> sorted = AccountKindCatalog.sorted();
        Assertions.assertEquals(BUILTIN_KIND_COUNT, sorted.size());
        Assertions.assertEquals(AccountKind.API_KEY, sorted.get(0).kind(), "API 密钥应排在最前");
        for (int i = 1; i < sorted.size(); i++) {
            Assertions.assertTrue(sorted.get(i - 1).order() <= sorted.get(i).order(),
                    "种类未按 order 升序: " + sorted.get(i - 1).kind() + " > " + sorted.get(i).kind());
        }
    }

    /**
     * 验证未知种类被拒绝，密钥类型回落到 {@code api}。
     */
    @Test
    @DisplayName("未知种类判为不支持，密钥类型回落 api")
    void unknownKindIsRejected() {
        Assertions.assertFalse(AccountKindCatalog.supported("UNKNOWN"), "未知种类不应通过校验");
        Assertions.assertFalse(AccountKindCatalog.supported(null), "空种类不应通过校验");
        Assertions.assertEquals("api", AccountKindCatalog.secretTypeOf("UNKNOWN"), "未知种类应回落 api");
        Assertions.assertEquals("mail", AccountKindCatalog.secretTypeOf("email"), "邮箱种类应映射 mail");
        Assertions.assertEquals("sms", AccountKindCatalog.secretTypeOf("SMS"), "短信种类应映射 sms");
    }

    /**
     * 验证能力位只声明真有实现的种类。
     */
    @Test
    @DisplayName("能力位只标真有实现的种类")
    void capabilitiesOnlyDeclareImplementedFeatures() {
        AccountKindProvider email = AccountKindCatalog.of(AccountKind.EMAIL);
        AccountKindProvider apiKey = AccountKindCatalog.of(AccountKind.API_KEY);
        AccountKindProvider sms = AccountKindCatalog.of(AccountKind.SMS);

        Assertions.assertNotNull(email);
        Assertions.assertNotNull(apiKey);
        Assertions.assertNotNull(sms);

        Set<AccountCapability> emailCaps = email.capabilities();
        Assertions.assertTrue(emailCaps.contains(AccountCapability.VERIFY), "邮箱有 AccountVerifier 实现，应支持校验");
        Assertions.assertTrue(emailCaps.contains(AccountCapability.INBOX), "邮箱有 AccountInboxProvider 实现，应支持收信");

        Assertions.assertFalse(apiKey.capabilities().contains(AccountCapability.VERIFY), "API 密钥无校验实现，不得标为支持");
        Assertions.assertFalse(apiKey.capabilities().contains(AccountCapability.INBOX), "API 密钥无收信实现，不得标为支持");
        Assertions.assertFalse(sms.capabilities().contains(AccountCapability.INBOX), "短信无收信实现，不得标为支持");
    }

    /**
     * 验证表单字段描述可序列化且键唯一（前端按 key 渲染，重复会互相覆盖）。
     */
    @Test
    @DisplayName("表单字段描述键唯一，含敏感字段标记")
    void fieldsAreUniqueAndMasked() {
        AccountKindCatalog.providers().values().forEach(provider -> {
            List<AccountField> fields = provider.fields();
            Assertions.assertFalse(fields.isEmpty(), "种类未声明表单字段: " + provider.kind());
            long distinct = fields.stream().map(AccountField::key).distinct().count();
            Assertions.assertEquals(fields.size(), distinct, "表单字段键重复: " + provider.kind());
            Assertions.assertTrue(fields.stream().anyMatch(AccountField::secret), "应至少声明一个敏感字段: " + provider.kind());
            Assertions.assertTrue(fields.stream().anyMatch(field -> "code".equals(field.key()) && field.required()),
                    "账号编码应为必填: " + provider.kind());
        });
    }

    /**
     * 验证 {@code GET /api/accounts/kinds} 的返回契约：老字段保持，新元数据齐全。
     */
    @Test
    @DisplayName("/kinds 返回契约：value/label 保持，元数据齐全")
    void kindsEndpointKeepsLegacyFieldsAndAddsMetadata() {
        AccountController controller = new AccountController(null);
        List<Map<String, Object>> kinds = controller.kinds().data();

        Assertions.assertEquals(BUILTIN_KIND_COUNT, kinds.size(), "内置种类应各一条: " + kinds);
        Map<String, Object> first = kinds.get(0);
        Assertions.assertEquals(AccountKind.API_KEY, first.get("value"), "value 字段保持老契约");
        Assertions.assertEquals("API 密钥", first.get("label"), "label 字段保持老契约");
        Assertions.assertNotNull(first.get("icon"), "缺少 icon");
        Assertions.assertEquals("api", first.get("secretType"), "缺少 secretType");
        Assertions.assertInstanceOf(List.class, first.get("fields"), "缺少 fields");
        Assertions.assertInstanceOf(Set.class, first.get("capabilities"), "缺少 capabilities");

        Set<Object> values = kinds.stream().map(item -> item.get("value")).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of(AccountKind.API_KEY, AccountKind.EMAIL, AccountKind.SMS), values,
                "种类值必须与内置常量完全一致，否则存量账号会变成未知种类");
    }
}
