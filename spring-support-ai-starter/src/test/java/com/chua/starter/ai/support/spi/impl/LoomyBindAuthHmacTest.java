// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoomyAccountChannelAuthManager} 绑定时 HMAC-SHA1 签名回归测试。
 *
 * <p>核心回归点（实测踩坑，线上 401）：{@code escapedPath} 对前导 {@code /} 的路径
 * 若按「每段前置斜杠」实现，会拼出 <b>{@code //login/thirdAccount/bind/auth}</b>（双斜杠），
 * 服务端按单斜杠重算签名 → {@code 401 HMAC signature does not match}，微信扫码绑定必败。
 * 本测试不做网络请求，直接反射调用私有 {@code signHeaders}，再用其产出的
 * Date/Nonce/Content-Type/Content-MD5 重算签名（路径取单斜杠），断言与 Authorization
 * 头中的签名一致 —— 服务端正是这样验签的。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
class LoomyBindAuthHmacTest {

    /** bind/auth 端点路径（签名按单斜杠计算）。 */
    private static final String PATH = "/login/thirdAccount/bind/auth";

    @Test
    @DisplayName("signHeaders：用请求头重算的签名与 Authorization 一致（单斜杠路径）")
    void signHeadersRecomputesFromSingleSlashPath() throws Exception {
        // 与真实 bindAuth 同构的请求体（base + param，仅做 Content-MD5 载体，路径才是验签关键）
        String body = "{\"base\":{\"appid\":\"GM3LOOMY\",\"modelid\":\"Web\",\"version\":\"1.0.0\","
                + "\"devid\":\"web\",\"ua\":\"Loomy|Desktop|Electron|macOS\",\"traceid\":\"t123\"},"
                + "\"param\":{\"tcode\":{\"code\":\"fake-code\"},\"type\":\"wx\"}}";

        LoomyAccountChannelAuthManager manager = new LoomyAccountChannelAuthManager();
        Method sign = LoomyAccountChannelAuthManager.class.getDeclaredMethod(
                "signHeaders", String.class, String.class, String.class);
        sign.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> headers =
                (Map<String, String>) sign.invoke(manager, "POST", PATH, body);

        // 1) 认证头形态：account {ak}:{sig}
        String ak = LoomyAccountChannelAuthManager.ACCESS_KEY_ID;
        String auth = headers.get("Authorization");
        assertNotNull(auth, "必须带 Authorization");
        assertTrue(auth.startsWith("account " + ak + ":"), "认证头应为 account {ak}:{sig}，实际: " + auth);
        String signature = auth.substring(("account " + ak + ":").length());
        assertTrue(signature.length() > 10, "签名不应为空");

        // 2) Content-MD5 必须等于发送 body 的 base64(MD5) —— 证明签名与发送共用同串
        String expectMd5 = Base64.getEncoder().encodeToString(md5(body));
        assertEquals(expectMd5, headers.get("Content-MD5"),
                "Content-MD5 须与请求体一致（签名与发送共用同一序列化串）");

        // 3) 核心回归：用【单斜杠】路径 + 请求头中的字段重算签名，必须与 Authorization 一致。
        //    若 escapedPath 拼出双斜杠，这里重算必然不等于客户端签名 → 服务端验签失败（401）。
        String recomputed = recomputeSignature(
                "POST", PATH,
                headers.get("Content-MD5"),
                headers.get("Content-Type"),
                headers.get("Date"),
                headers.get("Nonce"));
        assertEquals(signature, recomputed,
                "签名必须与单斜杠路径一致，不得出现 //（否则上游 401 HMAC signature does not match）");
    }

    @Test
    @DisplayName("escapedPath：前导斜杠绝不重复，尾斜杠剥除，无前导斜杠补上")
    void escapedPathSingleSlash() throws Exception {
        Method m = LoomyAccountChannelAuthManager.class.getDeclaredMethod("escapedPath", String.class);
        m.setAccessible(true);
        // 主路径回归：前导 / 只出现一次
        assertEquals(PATH, m.invoke(null, PATH), "前导斜杠不得重复");
        // 尾斜杠剥除
        assertEquals(PATH, m.invoke(null, PATH + "/"));
        // 无前导斜杠时补单个
        assertEquals(PATH, m.invoke(null, PATH.substring(1)));
        // 根路径：与参考实现（split('/').join('/')）一致地退化为空串
        assertEquals("", m.invoke(null, "/"));
        // 路径里的 % 等字符应被 RFC3986 转义（不参与本次回归，但确保不返回双斜杠）
        String weird = m.invoke(null, "/a%2Fb/c").toString();
        assertTrue(weird.startsWith("/a%252Fb/c"), "转义后仍不得出现双斜杠前缀，实际: " + weird);
    }

    /** base64(MD5(bytes))。 */
    private static byte[] md5(String body) throws Exception {
        return MessageDigest.getInstance("MD5").digest(body.getBytes(StandardCharsets.UTF_8));
    }

    /** 服务端验签算法：9 段 {@code \n} 拼接（后两段恒空），HMAC-SHA1 base64。 */
    private static String recomputeSignature(String method, String path, String md5,
                                             String contentType, String date, String nonce) throws Exception {
        String stringToSign = String.join("\n",
                method, path, "", md5 == null ? "" : md5,
                contentType == null ? "application/json" : contentType,
                date == null ? "" : date,
                nonce == null ? "" : nonce,
                "", "");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(
                LoomyAccountChannelAuthManager.ACCESS_KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
    }}