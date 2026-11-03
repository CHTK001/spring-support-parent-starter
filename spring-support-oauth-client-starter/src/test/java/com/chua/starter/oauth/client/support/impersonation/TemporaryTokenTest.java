// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.impersonation;

import com.chua.starter.oauth.client.support.user.UserResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临时令牌（管理员代入他人身份）的真实测试。
 *
 * <p>这是代入功能的<b>唯一限权手段</b>：框架的静态鉴权链路只判断「有没有令牌」，
 * 不比对 {@code expireTime}，即普通静态令牌一旦签发就长期有效。
 * 临时令牌的过期与路径限制完全依赖本类，任何一处被改坏都意味着
 * 「管理员关掉页面后，一个短期只读的代入令牌变成了永久有效的学生账号」，
 * 而编译、页面、日志都不会有任何提示。</p>
 *
 * <p>全部为纯静态逻辑验证，不需要容器、Redis 或任何打桩。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class TemporaryTokenTest {

    /**
     * 构造一个普通（非临时）身份。
     *
     * @return 普通身份
     */
    private static UserResult normalUser() {
        var user = new UserResult();
        user.setUserId("2026000001");
        user.setUsername("2026000001");
        return user;
    }

    /**
     * 标记必须同时写入「临时」标记、授权人，并把 readonly 置真。
     *
     * <p>{@code readonly} 是代入的核心约束：代入身份只应“看”，
     * 不应让管理员以学生身份改学生自己的学习数据。</p>
     */
    @Test
    @DisplayName("mark 写入标记、授权人，并把 readonly 置真")
    void mark_shouldSetFlagsAndReadonly() {
        var user = normalUser();
        TemporaryToken.mark(user, "admin", 300, List.of("/v2/edu/student"));

        assertTrue(TemporaryToken.isTemporary(user), "应被标记为临时令牌");
        assertEquals("admin", user.getExt().get(TemporaryToken.EXT_GRANTED_BY), "应记录授权人");
        assertTrue(user.isReadonly(), "代入身份必须只读");

        List<String> allowed = TemporaryToken.splitAllowedUrls(user);
        assertEquals(List.of("/v2/edu/student"), allowed, "应记录可访问路径");
    }

    /**
     * 授权人为空或空白时记为 unknown，不写入 null。
     */
    @Test
    @DisplayName("授权人为空时记为 unknown")
    void mark_shouldDefaultGrantedBy() {
        var userA = normalUser();
        TemporaryToken.mark(userA, null, 60, null);
        assertEquals("unknown", userA.getExt().get(TemporaryToken.EXT_GRANTED_BY));

        var userB = normalUser();
        TemporaryToken.mark(userB, "  ", 60, null);
        assertEquals("unknown", userB.getExt().get(TemporaryToken.EXT_GRANTED_BY));
    }

    /**
     * 标记不得丢弃身份原有的 ext 内容。
     *
     * <p>学生身份在 ext 里带着 studentId / grade 等业务字段，
     * 若 mark 直接覆盖 ext，学生端接口会因缺字段而查不到数据。</p>
     */
    @Test
    @DisplayName("mark 保留身份原有 ext 字段")
    void mark_shouldPreserveExistingExt() {
        var user = normalUser();
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("studentId", 42L);
        ext.put("accountType", "student");
        user.setExt(ext);

        TemporaryToken.mark(user, "admin", 300, List.of("/v2/edu/student"));

        assertEquals(42L, user.getExt().get("studentId"), "原有业务字段应保留");
        assertEquals("student", user.getExt().get("accountType"), "原有业务字段应保留");
        assertTrue(TemporaryToken.isTemporary(user), "标记应叠加而非替换");
    }

    /**
     * 传入 null 身份不得抛异常（鉴权链路上身份可能为空）。
     */
    @Test
    @DisplayName("mark(null) 安全返回，不抛异常")
    void mark_shouldTolerateNull() {
        TemporaryToken.mark(null, "admin", 300, List.of("/a"));
        assertFalse(TemporaryToken.isTemporary(null), "null 不应被当成临时令牌");
    }

    /**
     * 只有明确带 {@code true} 标记的才算临时令牌。
     */
    @Test
    @DisplayName("isTemporary 只认显式 true 标记")
    void isTemporary_shouldRequireTrueFlag() {
        assertFalse(TemporaryToken.isTemporary(null), "null 不是临时令牌");

        var noExt = normalUser();
        assertFalse(TemporaryToken.isTemporary(noExt), "无 ext 不是临时令牌");

        var emptyExt = normalUser();
        emptyExt.setExt(new LinkedHashMap<>());
        assertFalse(TemporaryToken.isTemporary(emptyExt), "空 ext 不是临时令牌");

        var falseFlag = normalUser();
        falseFlag.setExt(new LinkedHashMap<>(Map.of(TemporaryToken.EXT_TEMPORARY, Boolean.FALSE)));
        assertFalse(TemporaryToken.isTemporary(falseFlag), "显式 false 不是临时令牌");

        var stringFlag = normalUser();
        stringFlag.setExt(new LinkedHashMap<>(Map.of(TemporaryToken.EXT_TEMPORARY, "true")));
        assertFalse(TemporaryToken.isTemporary(stringFlag),
                "字符串 \"true\" 不是布尔 true，不应被当成临时令牌");

        var marked = normalUser();
        TemporaryToken.mark(marked, "admin", 60, null);
        assertTrue(TemporaryToken.isTemporary(marked), "mark 过的才是临时令牌");
    }

    /**
     * 普通令牌恒为可用——本类不介入它们的过期语义。
     *
     * <p>若这里返回 false，会把所有在线普通会话一起判失效。</p>
     */
    @Test
    @DisplayName("普通令牌恒为可用，即使 expireTime 为空或已过期")
    void isUsable_shouldAlwaysAllowNormalToken() {
        var noExpire = normalUser();
        assertTrue(TemporaryToken.isUsable(noExpire), "普通令牌无 expireTime 也应可用");

        var expired = normalUser();
        expired.setExpireTime(System.currentTimeMillis() / 1000L - 3600);
        assertTrue(TemporaryToken.isUsable(expired), "普通令牌即使已过期，本类也不介入");
    }

    /**
     * 临时令牌缺 expireTime 时判为不可用。
     *
     * <p>缺失说明令牌被截断或手工构造；放行等于给出一个永不过期的代入令牌。</p>
     */
    @Test
    @DisplayName("临时令牌缺 expireTime 判为不可用")
    void isUsable_shouldRejectTemporaryWithoutExpire() {
        var user = normalUser();
        TemporaryToken.mark(user, "admin", 300, null);
        user.setExpireTime(null);

        assertFalse(TemporaryToken.isUsable(user), "临时令牌无有效期必须判不可用");
    }

    /**
     * 临时令牌过期后必须判不可用。
     */
    @Test
    @DisplayName("临时令牌过期判为不可用，未过期判可用")
    void isUsable_shouldEnforceExpiry() {
        var expired = normalUser();
        TemporaryToken.mark(expired, "admin", 300, null);
        expired.setExpireTime(System.currentTimeMillis() / 1000L - 1);
        assertFalse(TemporaryToken.isUsable(expired), "已过期应判不可用");

        var alive = normalUser();
        TemporaryToken.mark(alive, "admin", 300, null);
        alive.setExpireTime(System.currentTimeMillis() / 1000L + 300);
        assertTrue(TemporaryToken.isUsable(alive), "未过期应判可用");
    }

    /**
     * 普通令牌不受路径限制。
     */
    @Test
    @DisplayName("普通令牌不受路径限制")
    void isPathAllowed_shouldAlwaysAllowNormalToken() {
        var user = normalUser();
        assertTrue(TemporaryToken.isPathAllowed(user, "/any/path"), "普通令牌应放行任意路径");
        assertTrue(TemporaryToken.isPathAllowed(user, null), "普通令牌连 null 路径也放行");
    }

    /**
     * 未配置 allowedUrls（或配置为 {@code *}）表示不限路径。
     */
    @Test
    @DisplayName("未配置路径清单或配置为 * 时不限路径")
    void isPathAllowed_shouldAllowWhenUnrestricted() {
        var noList = normalUser();
        TemporaryToken.mark(noList, "admin", 300, null);
        assertTrue(TemporaryToken.isPathAllowed(noList, "/anything"), "无路径清单应放行");

        var emptyList = normalUser();
        TemporaryToken.mark(emptyList, "admin", 300, List.of());
        assertTrue(TemporaryToken.isPathAllowed(emptyList, "/anything"), "空路径清单应放行");

        var any = normalUser();
        TemporaryToken.mark(any, "admin", 300, List.of("*"));
        assertTrue(TemporaryToken.isPathAllowed(any, "/anything"), "* 应放行任意路径");
    }

    /**
     * 路径按前缀匹配：命中清单内的前缀放行，清单外的拒绝。
     *
     * <p>这正是「代入令牌只能在学生端路径内活动、够不到管理端」的实现。</p>
     */
    @Test
    @DisplayName("路径前缀命中放行，清单外拒绝")
    void isPathAllowed_shouldMatchByPrefix() {
        var user = normalUser();
        TemporaryToken.mark(user, "admin", 300, List.of("/v2/edu/student", "/v2/edu/fun"));

        assertTrue(TemporaryToken.isPathAllowed(user, "/v2/edu/student"), "精确等于前缀应放行");
        assertTrue(TemporaryToken.isPathAllowed(user, "/v2/edu/student/me"), "子路径应放行");
        assertTrue(TemporaryToken.isPathAllowed(user, "/v2/edu/fun/modules"), "第二个前缀也应放行");

        assertFalse(TemporaryToken.isPathAllowed(user, "/v2/edu/dimension/students"),
                "清单外的管理端路径必须拒绝");
        assertFalse(TemporaryToken.isPathAllowed(user, "/v2/user/login"),
                "清单外的其它路径必须拒绝");
        assertFalse(TemporaryToken.isPathAllowed(user, "/"),
                "根路径必须拒绝");
    }

    /**
     * 空路径在受限情况下判否——不能因为「拿不到路径」就放行。
     */
    @Test
    @DisplayName("路径为空且受限时判否，不放行")
    void isPathAllowed_shouldRejectBlankPathWhenRestricted() {
        var user = normalUser();
        TemporaryToken.mark(user, "admin", 300, List.of("/v2/edu/student"));

        assertFalse(TemporaryToken.isPathAllowed(user, null), "null 路径应拒绝");
        assertFalse(TemporaryToken.isPathAllowed(user, ""), "空路径应拒绝");
        assertFalse(TemporaryToken.isPathAllowed(user, "   "), "全空白路径应拒绝");
    }

    /**
     * 路径清单按逗号拆分，并去除空白项。
     *
     * <p>清单在 ext 里以字符串形式存放，若拆分时不去空项，
     * 空字符串会成为一个「前缀」，进而放行任意路径。</p>
     */
    @Test
    @DisplayName("路径清单按逗号拆分且忽略空白项")
    void splitAllowedUrls_shouldSplitAndTrim() {
        var user = normalUser();
        user.setExt(new LinkedHashMap<>(Map.of(
                TemporaryToken.EXT_ALLOWED_URLS, " /a , /b ,  ,/c ,")));

        assertEquals(List.of("/a", "/b", "/c"), TemporaryToken.splitAllowedUrls(user),
                "应拆分、去空白、丢弃空项");

        assertEquals(List.of(), TemporaryToken.splitAllowedUrls(null), "null 身份返回空列表");

        var noKey = normalUser();
        noKey.setExt(new LinkedHashMap<>());
        assertEquals(List.of(), TemporaryToken.splitAllowedUrls(noKey), "无该键返回空列表");
    }

    /**
     * 路径清单里的空项不得成为「放行任意路径」的前缀。
     *
     * <p>清单在 ext 里是逗号分隔的字符串，若拆分时不过滤空项，
     * 空字符串会成为一个前缀，而 {@code path.startsWith("")} 恒为真
     * —— 等同于给了一个无限制的代入令牌。</p>
     */
    @Test
    @DisplayName("清单中的空项不放行任意路径")
    void isPathAllowed_shouldNotTreatEmptyEntryAsPrefix() {
        // 全是空项：解析后为空列表，等同「未配置」，语义上不限制（放行）
        var allBlank = normalUser();
        allBlank.setExt(new LinkedHashMap<>(Map.of(TemporaryToken.EXT_TEMPORARY, Boolean.TRUE,
                TemporaryToken.EXT_ALLOWED_URLS, " , ")));
        assertTrue(TemporaryToken.isPathAllowed(allBlank, "/whatever"),
                "清单全为空项时应等同未配置（放行），而不是被空前缀放行任意路径");

        // 空项 + 有效项：空项必须被丢弃，只有有效前缀生效
        var mixed = normalUser();
        mixed.setExt(new LinkedHashMap<>(Map.of(TemporaryToken.EXT_TEMPORARY, Boolean.TRUE,
                TemporaryToken.EXT_ALLOWED_URLS, " , /only")));
        assertFalse(TemporaryToken.isPathAllowed(mixed, "/other"),
                "空项不应成为放行任意路径的前缀");
        assertTrue(TemporaryToken.isPathAllowed(mixed, "/only/x"),
                "有效前缀仍应放行");
    }

    /**
     * mark 不修改传入的路径清单对象。
     */
    @Test
    @DisplayName("mark 不修改调用方传入的路径清单")
    void mark_shouldNotMutateInputList() {
        var input = new ArrayList<>(Arrays.asList("/a", "/b"));
        TemporaryToken.mark(normalUser(), "admin", 60, input);

        assertEquals(List.of("/a", "/b"), input, "传入的清单不应被就地修改");
    }

    /**
     * 工具类不可实例化。
     */
    @Test
    @DisplayName("工具类构造器私有")
    void constructor_shouldBePrivate() throws Exception {
        var ctor = TemporaryToken.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                "工具类必须有 private 构造器");
    }
}