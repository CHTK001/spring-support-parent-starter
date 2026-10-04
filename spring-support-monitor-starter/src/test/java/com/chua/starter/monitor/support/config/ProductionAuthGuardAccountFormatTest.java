/**
 * 静态账号配置「格式非法」的判定门禁。
 *
 * <p>这个门禁针对的是一个已确认的缺口：{@code ProductionAuthGuard}
 * 原本只判空，于是「格式错误」的凭据能一路通过守卫。结果是
 * <b>服务正常启动、配置上也标着「已启用鉴权」，但没有任何账号能登录</b> ——
 * 与「完全没配账号」是同一种故障，却完全躲过了原先的检查。</p>
 *
 * <p>最容易踩的坑由配置文档自己提供：{@code application-prod.yml} 曾写着
 * 「用 {@code openssl passwd -apr1} 生成」。而该命令产出的是
 * {@code $apr1$<盐>$<哈希>}，{@code SaltedPassword.isSalted} 要求的是
 * <b>严格 32 位十六进制盐 + {@code $} + 严格 32 位十六进制摘要</b>，
 * 于是永远匹配不上。守卫放行 → 服务起来 → 谁也登不进去。</p>
 *
 * <p>本 spec 的最后一组是<b>敏感性对照</b>：把守卫退回「只判空」的写法，
 * 这些用例必须变红，否则它们就是在为一段恒真的代码背书。</p>
 */
package com.chua.starter.monitor.support.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静态账号格式校验。
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("生产鉴权守卫：静态账号格式校验")
class ProductionAuthGuardAccountFormatTest {

    /** 32 位十六进制盐 */
    private static final String SALT = "0123456789abcdef0123456789abcdef";

    /** 32 位十六进制摘要 */
    private static final String DIGEST = "fedcba9876543210fedcba9876543210";

    /** 合法的加盐口令 */
    private static final String SALTED = SALT + "$" + DIGEST;

    @Test
    @DisplayName("合法的加盐摘要通过校验（不得把能用的配置误判成有问题的）")
    void saltedCredentialIsAccepted() {
        assertTrue(ProductionAuthGuard.detectMalformedAccounts("admin:" + SALTED).isEmpty(),
                "合法加盐口令必须放行：守卫一旦在正常配置上误报，就会被人当噪声关掉");
    }

    @Test
    @DisplayName("明文口令通过校验（StaticUserStore 明确支持，不得误报）")
    void plaintextCredentialIsAccepted() {
        assertTrue(ProductionAuthGuard.detectMalformedAccounts("admin:Passw0rd!").isEmpty());
    }

    @Test
    @DisplayName("openssl passwd -apr1 的 $apr1$ 格式被拒（本文件存在的主要理由）")
    void apr1CredentialIsRejected() {
        String apr1 = "$apr1$AbCdEfGh$0123456789abcdefghijklmnopqrstuv";
        List<String> reasons =
                ProductionAuthGuard.detectMalformedAccounts("admin:" + apr1);

        assertEquals(1, reasons.size(), "$apr1$ 格式必须被判为非法");
        assertTrue(reasons.getFirst().contains("加盐摘要"),
                "原因里要点明是加盐摘要格式问题，实际=" + reasons.getFirst());
    }

    @Test
    @DisplayName("原因文案点名 openssl passwd —— 这是文档曾经引导的错误命令")
    void apr1ReasonMentionsOpenssl() {
        String apr1 = "$apr1$AbCdEfGh$0123456789abcdefghijklmnopqrstuv";
        assertTrue(ProductionAuthGuard.detectMalformedAccounts("admin:" + apr1).getFirst()
                .contains("openssl passwd"),
                "报错必须直接指出 openssl passwd -apr1 不被识别，否则运维会去查别的地方");
    }

    @Test
    @DisplayName("盐长度不足 32 位被拒")
    void shortSaltIsRejected() {
        assertEquals(1, ProductionAuthGuard.detectMalformedAccounts("admin:0123$" + DIGEST).size());
    }

    @Test
    @DisplayName("摘要长度不足 32 位被拒")
    void shortDigestIsRejected() {
        assertEquals(1, ProductionAuthGuard.detectMalformedAccounts("admin:" + SALT + "$abcd").size());
    }

    @Test
    @DisplayName("非十六进制字符被拒（长度对但内容不是 hex）")
    void nonHexIsRejected() {
        String badSalt = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz";
        assertEquals(1, ProductionAuthGuard.detectMalformedAccounts("admin:" + badSalt + "$" + DIGEST).size());
    }

    @Test
    @DisplayName("缺少冒号的条目被拒")
    void missingColonIsRejected() {
        List<String> reasons = ProductionAuthGuard.detectMalformedAccounts("admin");
        assertEquals(1, reasons.size());
        assertTrue(reasons.getFirst().contains("冒号"));
    }

    @Test
    @DisplayName("用户名为空的条目被拒")
    void blankUsernameIsRejected() {
        List<String> reasons = ProductionAuthGuard.detectMalformedAccounts(":" + SALTED);
        assertEquals(1, reasons.size());
        assertTrue(reasons.getFirst().contains("用户名"));
    }

    @Test
    @DisplayName("口令为空的条目被拒（否则等于开了一个空口令账号）")
    void blankSecretIsRejected() {
        List<String> reasons = ProductionAuthGuard.detectMalformedAccounts("admin:");
        assertEquals(1, reasons.size());
        assertTrue(reasons.getFirst().contains("没有口令"));
    }

    @Test
    @DisplayName("多账号时只报出问题的那一个，合法的放行")
    void reportsOnlyBadEntryAmongMany() {
        List<String> reasons = ProductionAuthGuard.detectMalformedAccounts(
                "admin:" + SALTED + ";ops:$apr1$xx$yy;viewer:plainpw");
        assertEquals(1, reasons.size(), "实际=" + reasons);
        assertTrue(reasons.getFirst().contains("ops"));
    }

    @Test
    @DisplayName("分号两侧的空格被容忍（配置里常带空格）")
    void surroundingSpacesAreTolerated() {
        assertTrue(ProductionAuthGuard.detectMalformedAccounts("  admin :" + SALTED + " ; ")
                .isEmpty());
    }

    @Test
    @DisplayName("空条目被忽略而不是报错（尾随分号不该让服务起不来）")
    void blankEntriesIgnored() {
        assertTrue(ProductionAuthGuard.detectMalformedAccounts("admin:" + SALTED + ";;;").isEmpty());
    }

    @Test
    @DisplayName("null / 空串不报错（空值由 detectUnusableConfigs 的空检查负责）")
    void nullAndBlankAreSafe() {
        assertTrue(ProductionAuthGuard.detectMalformedAccounts(null).isEmpty());
        assertTrue(ProductionAuthGuard.detectMalformedAccounts("").isEmpty());
        assertTrue(ProductionAuthGuard.detectMalformedAccounts("   ").isEmpty());
    }

    @Test
    @DisplayName("经由 detectUnusableConfigs 时格式错误也会被报出（整合点必须真的生效）")
    void flowsThroughDetectUnusableConfigs() {
        String apr1 = "$apr1$AbCdEfGh$0123456789abcdefghijklmnopqrstuv";
        List<String> reasons = ProductionAuthGuard.detectUnusableConfigs("static", "admin:" + apr1);

        assertFalse(reasons.isEmpty(),
                "只调用 detectUnusableConfigs 也必须能报出格式问题 —— "
                        + "否则守卫实际装配时走的正是这条路径，单独测 detectMalformedAccounts 等于没测");
        assertTrue(reasons.getFirst().contains("加盐摘要"));
    }

    @Test
    @DisplayName("空账号仍然只报「未配置」一条（不得被格式检查重复计数）")
    void blankAccountStillReportsSingleReason() {
        assertEquals(1, ProductionAuthGuard.detectUnusableConfigs("static", "").size());
        assertEquals(1, ProductionAuthGuard.detectUnusableConfigs("static", null).size());
    }

    @Test
    @DisplayName("合法账号经由 detectUnusableConfigs 时无任何原因")
    void validAccountPassesThrough() {
        assertTrue(ProductionAuthGuard.detectUnusableConfigs("static", "admin:" + SALTED).isEmpty());
        assertTrue(ProductionAuthGuard.detectUnusableConfigs("static", "admin:plainpw").isEmpty());
    }

    @Test
    @DisplayName("非 static 协议不做格式校验（凭据在远端，查不到不等于没有）")
    void nonStaticProtocolIsNotFormatChecked() {
        assertTrue(ProductionAuthGuard.detectUnusableConfigs("tcp", "admin:$apr1$xx$yy").isEmpty(),
                "tcp/oauth2 的凭据由远端提供，本进程看不到，误报会让守卫被当成噪声关掉");
        assertTrue(ProductionAuthGuard.detectUnusableConfigs(null, "admin:$apr1$xx$yy").isEmpty());
    }
}