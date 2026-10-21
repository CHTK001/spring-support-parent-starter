package com.chua.starter.ai.support.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PrivateAddressGuard} 的用例。
 *
 * <p>用例分两边：**必须拦下**的（各种写法指向本机/内网）与**必须放行**的
 * （正常公网地址、模型 id）。只测「拦住」是不够的 —— 一个永远返回 true 的实现
 * 也能通过那半边，于是普通账号连默认模型都改不了。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
class PrivateAddressGuardTest {

    @Test
    @DisplayName("本机与内网的常见写法都必须拦下")
    void rejectsLocalAndPrivateTargets() {
        String[] bad = {
                "http://127.0.0.1:11434/v1",
                "http://127.1.2.3/v1",
                "http://localhost:8080/v1",
                "https://LOCALHOST/v1",
                "http://0.0.0.0:9000",
                "http://[::1]:8080/v1",
                "http://::1/v1",
                "http://10.0.0.5/v1",
                "http://192.168.1.10:11434",
                "http://172.16.0.1/v1",
                "http://172.31.255.254/v1",
                // 云上实例元数据：读内网凭据最常用的入口
                "http://169.254.169.254/latest/meta-data/",
                // 十进制 / 十六进制混淆写法
                "http://2130706433/v1",
                "http://0x7f000001/v1",
                // 容器/局域网内的常见别名
                "http://host.docker.internal:11434",
                "http://ollama.local:11434/v1",
                "http://db.internal:3306",
                // 本地伪协议
                "file:///etc/passwd",
                "jar:file:/opt/ai-starter/app.jar!/",
                // 模型 id 里直接嵌地址：字段名看不出问题，只能看值
                "custom:http://127.0.0.1:1234/v1",
        };
        for (String v : bad) {
            assertTrue(PrivateAddressGuard.looksLikePrivateTarget(v), "应拦下: " + v);
        }
    }

    @Test
    @DisplayName("公网地址、模型 id 与空值都必须放行")
    void allowsPublicAndBenignValues() {
        String[] good = {
                "https://api.openai.com/v1",
                "https://apihub.agnes-ai.com/v1",
                "http://8.8.8.8/v1",
                "https://api.deepseek.com",
                "gz-1",
                "gpt-4o",
                "onnx:paddleocrv6-det",
                "agnes:agnes-3.0-flash",
                "deepseek-v4.1-flash",
                "zh-CN",
                "true",
                "0.7",
                "16384",
                // 短数字不能被当成 32 位 IP 解读：11434 是端口，不是 0.0.44.170
                "11434",
                "",
                "   ",
        };
        for (String v : good) {
            assertFalse(PrivateAddressGuard.looksLikePrivateTarget(v), "应放行: [" + v + "]");
        }
        assertFalse(PrivateAddressGuard.looksLikePrivateTarget(null), "null 应放行");
        // 形似而不是的域名：不能被 .local/.internal 的子串规则误伤
        assertFalse(PrivateAddressGuard.looksLikePrivateTarget("https://localize.example.com/v1"),
                "localize 不是 localhost");
        assertFalse(PrivateAddressGuard.looksLikePrivateTarget("https://myinternal.example.com/v1"),
                "myinternal 不以 .internal 结尾");
    }
}
