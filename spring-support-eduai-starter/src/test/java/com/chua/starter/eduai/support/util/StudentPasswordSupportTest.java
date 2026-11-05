// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.support.util;

import com.chua.starter.common.support.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 学生口令摘要与校验的真实测试。
 *
 * <p>这是学生登录的安全核心：摘要算法、加盐、定长比较、强度校验四项任一被改坏，
 * 都会直接表现为「登录异常」或「口令可被爆破」，而编译与页面都不会给出任何提示。
 * 因此本测试把这些行为逐条固化。</p>
 *
 * <p>全部为纯函数验证，不需要容器、数据库或任何打桩；这也正是它值得被测的原因
 * ——成本极低，而失败代价极高。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class StudentPasswordSupportTest {

    /**
     * 口令摘要的十六进制长度（HMAC-SHA256 为 32 字节）
     */
    private static final int HASH_HEX_LENGTH = 64;

    /**
     * 盐的十六进制长度（16 字节）
     */
    private static final int SALT_HEX_LENGTH = 32;

    /**
     * 盐必须够长且是十六进制，且两次生成不能相同。
     *
     * <p>盐若可预测（或退化为常量），同一口令在所有学生处的摘要就会相同，
     * 一张彩虹表即可批量比对整表口令。</p>
     */
    @Test
    @DisplayName("盐为 32 位十六进制且每次不同")
    void newSalt_shouldBeHexAndRandom() {
        Set<String> salts = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String salt = StudentPasswordSupport.newSalt();
            assertNotNull(salt, "盐不应为 null");
            assertEquals(SALT_HEX_LENGTH, salt.length(), "盐长度应为 32 位十六进制");
            assertTrue(salt.matches("[0-9a-f]{32}"), "盐只能由小写十六进制字符组成，实际: " + salt);
            salts.add(salt);
        }
        assertEquals(20, salts.size(), "连续 20 次生成的盐不应出现重复");
    }

    /**
     * 同一口令 + 同一盐必须得到同一摘要（否则存进去就再也验不过）。
     */
    @Test
    @DisplayName("相同口令与盐得到确定摘要，长度 64 位十六进制")
    void hash_shouldBeDeterministic() {
        String salt = StudentPasswordSupport.newSalt();
        String first = StudentPasswordSupport.hash("stu123456", salt);
        String second = StudentPasswordSupport.hash("stu123456", salt);

        assertNotNull(first);
        assertEquals(HASH_HEX_LENGTH, first.length(), "HMAC-SHA256 的十六进制摘要为 64 位");
        assertEquals(first, second, "相同输入必须得到相同摘要");
    }

    /**
     * 同一口令在不同盐下摘要必须不同——这是「加盐」存在的全部意义。
     */
    @Test
    @DisplayName("同一口令在不同盐下摘要不同")
    void hash_shouldDifferBySalt() {
        String saltA = StudentPasswordSupport.newSalt();
        String saltB = StudentPasswordSupport.newSalt();
        String hashA = StudentPasswordSupport.hash("same-password", saltA);
        String hashB = StudentPasswordSupport.hash("same-password", saltB);

        assertNotEquals(hashA, hashB, "换了盐摘要必须变化，否则盐没有生效");
    }

    /**
     * 空口令或空盐必须返回 null，而不是给出一个「弱化版」摘要。
     *
     * <p>若空盐能算出摘要，攻击者可用空盐摘要直接构造可通过校验的记录。</p>
     */
    @Test
    @DisplayName("空口令或空盐返回 null，不产出弱化摘要")
    void hash_shouldRejectBlank() {
        assertNull(StudentPasswordSupport.hash(null, "somesalt"));
        assertNull(StudentPasswordSupport.hash("", "somesalt"));
        assertNull(StudentPasswordSupport.hash("  ", "somesalt"));
        assertNull(StudentPasswordSupport.hash("password", null));
        assertNull(StudentPasswordSupport.hash("password", ""));
        assertNull(StudentPasswordSupport.hash("password", "   "));
    }

    /**
     * 正确口令匹配、错误口令不匹配。
     */
    @Test
    @DisplayName("正确口令匹配，错误口令不匹配")
    void matches_shouldVerifyPassword() {
        String salt = StudentPasswordSupport.newSalt();
        String stored = StudentPasswordSupport.hash("correct-horse", salt);

        assertTrue(StudentPasswordSupport.matches("correct-horse", salt, stored), "正确口令应匹配");
        assertFalse(StudentPasswordSupport.matches("wrong-horse", salt, stored), "错误口令不应匹配");
        assertFalse(StudentPasswordSupport.matches("correct-hors", salt, stored), "少一个字符不应匹配");
        assertFalse(StudentPasswordSupport.matches("Correct-Horse", salt, stored), "大小写敏感");
    }

    /**
     * 换了盐之后，即使口令正确也不应匹配。
     *
     * <p>防止「校验时误用另一个盐」这类实现错误：那会让所有学生在某种条件下都能登录。</p>
     */
    @Test
    @DisplayName("盐不匹配时正确口令也验不过")
    void matches_shouldFailWithWrongSalt() {
        String salt = StudentPasswordSupport.newSalt();
        String stored = StudentPasswordSupport.hash("real-password", salt);
        String otherSalt = StudentPasswordSupport.newSalt();

        assertFalse(StudentPasswordSupport.matches("real-password", otherSalt, stored),
                "用另一个盐校验必须失败，否则盐被绕过");
    }

    /**
     * 任一入参为空一律返回 false，不得因空值放行。
     */
    @Test
    @DisplayName("任一入参为空返回 false，不放行")
    void matches_shouldRejectBlankInputs() {
        String salt = StudentPasswordSupport.newSalt();
        String stored = StudentPasswordSupport.hash("pwd123456", salt);

        assertFalse(StudentPasswordSupport.matches(null, salt, stored));
        assertFalse(StudentPasswordSupport.matches("", salt, stored));
        assertFalse(StudentPasswordSupport.matches("pwd123456", null, stored));
        assertFalse(StudentPasswordSupport.matches("pwd123456", salt, null));
        assertFalse(StudentPasswordSupport.matches("pwd123456", salt, ""));

        // 摘要为空时尤其不能放行：库中未设口令的学生不该能用任意口令登录
        assertFalse(StudentPasswordSupport.matches("anything", salt, ""));
    }

    /**
     * 摘要长度与库中不一致时直接判否，不进入逐字符比较。
     */
    @Test
    @DisplayName("摘要长度不一致时判否")
    void matches_shouldRejectDifferentLengthHash() {
        String salt = StudentPasswordSupport.newSalt();
        assertFalse(StudentPasswordSupport.matches("pwd123456", salt, "short"));
        assertFalse(StudentPasswordSupport.matches("pwd123456", salt, "0".repeat(HASH_HEX_LENGTH + 1)));
    }

    /**
     * 强度校验的上下边界：6 到 32 位。
     */
    @Test
    @DisplayName("口令长度边界：5 位拒绝、6 位通过、32 位通过、33 位拒绝")
    void validate_shouldEnforceLengthBoundary() {
        assertThrows(BusinessException.class, () -> StudentPasswordSupport.validate("12345"),
                "5 位应被拒绝");
        assertDoesNotThrow(() -> StudentPasswordSupport.validate("123456"),
                "6 位应通过（下界含）");
        assertDoesNotThrow(() -> StudentPasswordSupport.validate("a".repeat(32)),
                "32 位应通过（上界含）");
        assertThrows(BusinessException.class, () -> StudentPasswordSupport.validate("a".repeat(33)),
                "33 位应被拒绝");
    }

    /**
     * 空口令与全空白口令必须被拒。
     */
    @Test
    @DisplayName("空口令与全空白口令被拒，且提示可读")
    void validate_shouldRejectBlankWithReadableMessage() {
        var nullEx = assertThrows(BusinessException.class,
                () -> StudentPasswordSupport.validate(null));
        assertTrue(nullEx.getMessage().contains("不能为空"),
                "空口令提示应说明原因，实际: " + nullEx.getMessage());

        var blankEx = assertThrows(BusinessException.class,
                () -> StudentPasswordSupport.validate("      "));
        assertTrue(blankEx.getMessage().contains("不能为空"), "全空白同样按空处理");

        var shortEx = assertThrows(BusinessException.class,
                () -> StudentPasswordSupport.validate("abc"));
        assertTrue(shortEx.getMessage().contains("6") && shortEx.getMessage().contains("32"),
                "长度越界提示应给出合法区间，实际: " + shortEx.getMessage());
    }

    /**
     * 口令含中文等多字节字符时按「字符数」而非「字节数」计量。
     *
     * <p>否则一个 6 个汉字的合法口令会被按 18 字节判超长而拒绝。</p>
     */
    @Test
    @DisplayName("多字节字符按字符数计量")
    void validate_shouldCountCharactersNotBytes() {
        assertDoesNotThrow(() -> StudentPasswordSupport.validate("学生口令六个字"),
                "6 个汉字应视为 6 位，而不是 18 字节");
        assertThrows(BusinessException.class, () -> StudentPasswordSupport.validate("五个字口令"),
                "5 个汉字应被拒绝");
    }

    /**
     * 工具类不可实例化（私有构造器），避免被当成有状态对象使用。
     */
    @Test
    @DisplayName("工具类构造器私有")
    void constructor_shouldBePrivate() throws Exception {
        var ctor = StudentPasswordSupport.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                "工具类必须有 private 构造器");
    }}