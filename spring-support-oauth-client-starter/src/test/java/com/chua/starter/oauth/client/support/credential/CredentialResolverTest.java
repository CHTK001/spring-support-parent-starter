package com.chua.starter.oauth.client.support.credential;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凭据解析顺序与请求规范化的真实测试。
 *
 * <p>这两处共同决定「学生登录走哪个凭据实现」：</p>
 * <ul>
 *   <li>{@link CredentialResolver} 的解析顺序是
 *       自定义同名 → {@code builtin-} 前缀 → {@code null}（交回既有逻辑）。
 *       顺序错了会出现「学生登录被内置 WEB 实现接管」或反之，
 *       表现为登录永远失败或凭据被绕过。</li>
 *   <li>{@link CredentialRequest} 的规范化保证 {@code accountType} 缺省时
 *       回落 {@link AuthType}，使老调用方行为与引入扩展点之前完全一致。</li>
 * </ul>
 *
 * <p>注册到 SPI 容器时使用 {@code unittest-} 前缀的独特名字，避免与真实实现冲突。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class CredentialResolverTest {

    /**
     * 构造一个可辨识的假实现（不做任何校验，只用于确认「解析到了哪一个」）。
     *
     * <p>刻意用匿名类而非 lambda：JLS 规定<b>无捕获的 lambda 每次求值返回同一实例</b>
     * （实现为单例），于是「自定义」与「内置」两个探针会指向同一个对象，
     * {@code assertNotSame} 必然失败。匿名类每次 {@code new} 都是新实例，
     * 才能区分「命中了哪一个」。</p>
     *
     * @return 假实现
     */
    private static CredentialProvider probe() {
        return new CredentialProvider() {

            @Override
            public LoginAuthResult authenticate(CredentialRequest request) {
                return null;
            }
        };
    }

    /**
     * null 与空白账号来源一律返回 null，不触 SPI。
     */
    @Test
    @DisplayName("null / 空白账号来源返回 null")
    void resolve_shouldReturnNullForBlank() {
        assertNull(CredentialResolver.resolve((String) null), "null 应返回 null");
        assertNull(CredentialResolver.resolve(""), "空串应返回 null");
        assertNull(CredentialResolver.resolve("   "), "全空白应返回 null");
        assertNull(CredentialResolver.resolve((CredentialRequest) null), "null 请求应返回 null");
    }

    /**
     * 未注册的账号来源返回 null（调用方据此回落到既有逻辑）。
     */
    @Test
    @DisplayName("未注册的账号来源返回 null")
    void resolve_shouldReturnNullForUnknownType() {
        assertNull(CredentialResolver.resolve("unittest-not-registered-xyz"),
                "未注册应返回 null，而不是抛异常或被内置实现接管");
    }

    /**
     * 自定义实现优先于 {@code builtin-} 同名实现。
     *
     * <p>这是前缀约定的全部意义：让业务方用 {@code student} 覆盖内置，
     * 而不需要改内置类或重写注册。</p>
     */
    @Test
    @DisplayName("自定义同名实现优先于 builtin- 内置实现")
    void resolve_shouldPreferCustomOverBuiltin() {
        var customType = "unittest-prefer";
        CredentialProvider custom = probe();
        CredentialProvider builtin = probe();

        ServiceProvider.of(CredentialProvider.class).register(customType, custom);
        ServiceProvider.of(CredentialProvider.class).register("builtin-" + customType, builtin);

        assertSame(custom, CredentialResolver.resolve(customType),
                "同名自定义实现必须优先命中");
        assertNotSame(builtin, CredentialResolver.resolve(customType),
                "内置实现不应抢先");
    }

    /**
     * 没有自定义实现时才回落到 {@code builtin-} 内置实现。
     */
    @Test
    @DisplayName("无自定义实现时回落到 builtin- 内置实现")
    void resolve_shouldFallbackToBuiltin() {
        var builtinType = "unittest-only-builtin";
        CredentialProvider builtin = probe();
        ServiceProvider.of(CredentialProvider.class).register("builtin-" + builtinType, builtin);

        assertSame(builtin, CredentialResolver.resolve(builtinType),
                "只有内置实现时应命中内置");
    }

    /**
     * 账号来源的大小写与首尾空白必须不敏感。
     *
     * <p>前端可能传 {@code STUDENT}、{@code Student} 或带空格的 {@code " student "}，
     * 三种都必须解析到同一个实现，否则登录会因大小写不同而随机失败。</p>
     */
    @Test
    @DisplayName("账号来源大小写与首尾空白不敏感")
    void resolve_shouldBeCaseAndWhitespaceInsensitive() {
        var type = "unittest-case";
        CredentialProvider impl = probe();
        ServiceProvider.of(CredentialProvider.class).register(type, impl);

        assertSame(impl, CredentialResolver.resolve(type), "小写应命中");
        assertSame(impl, CredentialResolver.resolve(type.toUpperCase()), "大写应命中");
        assertSame(impl, CredentialResolver.resolve("  " + type + "  "), "首尾空白应被忽略");
        assertSame(impl, CredentialResolver.resolve(Character.toUpperCase(type.charAt(0)) + type.substring(1)),
                "首字母大写应命中");
    }

    /**
     * 请求的账号来源缺省时回落 {@link AuthType}，保证老调用方行为不变。
     */
    @Test
    @DisplayName("accountType 缺省时回落 AuthType")
    void effectiveAccountType_shouldFallbackToAuthType() {
        var withAccountType = new CredentialRequest("stu", "pwd", AuthType.WEB, "student", Map.of(), 0);
        assertEquals("student", withAccountType.effectiveAccountType(), "显式账号来源优先");

        var withoutAccountType = new CredentialRequest("stu", "pwd", AuthType.WEB, null, Map.of(), 0);
        assertEquals("web", withoutAccountType.effectiveAccountType(), "缺省应回落 AuthType 的 code");

        var noAuthType = new CredentialRequest("stu", "pwd", null, null, Map.of(), 0);
        assertEquals(AuthType.NONE.getCode(), noAuthType.effectiveAccountType(),
                "两者都为空时回落 NONE，而不是抛异常");
    }

    /**
     * accountType 在构造时即被规范化（小写 + 去空白）。
     */
    @Test
    @DisplayName("accountType 构造时规范化")
    void accountType_shouldBeNormalized() {
        var req = new CredentialRequest("a", "b", AuthType.WEB, "  STUDENT  ", Map.of(), 0);
        assertEquals("student", req.accountType(), "应小写并去首尾空白");
        assertEquals("student", req.effectiveAccountType());
    }

    /**
     * ext 必须做防御性拷贝：调用方之后修改原 Map 不应影响请求。
     */
    @Test
    @DisplayName("ext 做防御性拷贝，外部修改不影响请求")
    void ext_shouldBeDefensivelyCopied() {
        Map<String, Object> source = new HashMap<>();
        source.put("k", "v1");

        var req = new CredentialRequest("a", "b", AuthType.WEB, null, source, 0);
        source.put("k", "v2");
        source.put("added", "later");

        assertEquals("v1", req.extValue("k"), "外部修改不应影响请求内的快照");
        assertNull(req.extValue("added"), "后续新增的键不应出现");
    }

    /**
     * ext 允许 null 值（设计上有意不用 {@code Map.copyOf}）。
     */
    @Test
    @DisplayName("ext 允许 null 值，不抛异常")
    void ext_shouldTolerateNullValues() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("nullable", null);

        var req = new CredentialRequest("a", "b", AuthType.WEB, null, withNull, 0);
        assertNull(req.extValue("nullable"), "null 值应原样保留");

        var empty = new CredentialRequest("a", "b", AuthType.WEB, null, null, 0);
        assertEquals(Map.of(), empty.ext(), "ext 为 null 时归一为空映射");
    }

    /**
     * 快照不可变：外部拿到 ext 后写入必须被拒。
     */
    @Test
    @DisplayName("ext 快照不可变")
    void ext_shouldBeImmutable() {
        var req = new CredentialRequest("a", "b", AuthType.WEB, null, Map.of("k", "v"), 0);
        assertThrows(UnsupportedOperationException.class,
                () -> req.ext().put("new", "value"),
                "ext 应是不可变视图");
    }

    /**
     * extValue 对 null 键安全返回。
     */
    @Test
    @DisplayName("extValue(null) 安全返回 null")
    void extValue_shouldTolerateNullKey() {
        var req = new CredentialRequest("a", "b", AuthType.WEB, null, Map.of("k", "v"), 0);
        assertNull(req.extValue(null));
        assertNull(req.extValue("absent"));
    }

    /**
     * 解析只读不写：多次 resolve 同一账号来源必须得到同一实例。
     */
    @Test
    @DisplayName("同一账号来源多次解析得到同一实例")
    void resolve_shouldBeStable() {
        var type = "unittest-stable";
        CredentialProvider impl = probe();
        ServiceProvider.of(CredentialProvider.class).register(type, impl);

        List<CredentialProvider> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(CredentialResolver.resolve(type));
        }
        assertTrue(results.stream().allMatch(r -> r == impl),
                "多次解析不应返回不同实例（无状态、无竞态）");
    }

    /**
     * 工具类不可实例化。
     */
    @Test
    @DisplayName("解析器构造器私有")
    void constructor_shouldBePrivate() throws Exception {
        var ctor = CredentialResolver.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                "工具类必须有 private 构造器");
    }
}