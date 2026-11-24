package com.chua.starter.datasearch.credit.support;

import com.chua.common.support.utils.DigestUtils;
import com.chua.starter.datasearch.credit.model.CreditQuery;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * 云厂商余额接口的 AK/SK 签名（只读出口）。
 *
 * <p>有六类厂商的余额接口要按 AK/SK 对请求做 HMAC 签名，只发 Bearer 是查不到的。
 * 这里按各家官方文档原文实现签名，且<b>签名用的编码与真正发出去的请求共用同一份
 * 字符串</b>：{@link #sign(CreditQuery, String)} 直接产出「完整 URL + 最终请求头 + body」，
 * {@link CreditHttp} 拿去发请求时不再做任何二次编码或补头，避免签名字节与实际请求
 * 不一致（这类错位只会得到上层 401，很难排查）。</p>
 *
 * <p>收录的七种方案（{@link CreditQuery#getSignType()}，由 host 预设按厂商注入，
 * 页面不必手填）：</p>
 * <ul>
 *   <li>{@code qiniu} — 七牛管理凭证，{@code HMAC-SHA1} + URL-safe Base64（带 {@code =} padding），
 *       {@code Authorization: Qiniu <AccessKey>:<encodedSign>}；
 *       文档：{@code https://developer.qiniu.com/kodo/1201/access-token}，
 *       编码细节按官方 Java SDK {@code Auth.signQiniuAuthorization} 对齐。</li>
 *   <li>{@code volc} — 火山引擎 {@code HMAC-SHA256}，逐级派生签名密钥，
 *       {@code Authorization: HMAC-SHA256 Credential=..., SignedHeaders=host;x-date, Signature=...}；
 *       文档：{@code https://docs.volcengine.com/docs/api-signature-call-guide/signature-method}。</li>
 *   <li>{@code volc-ark} — 火山方舟 OpenAPI 统一入口（{@code POST /?Action=...&Version=...}），
 *       与 {@code volc} 同一套 {@code HMAC-SHA256} 派生链（service=ark、region=cn-beijing），
 *       但为 POST + JSON body：签名头域 {@code content-type;host;x-content-sha256;x-date}，
 *       body 摘要参与签名；签名规范同 {@code volc} 的官方签名指南。</li>
 *   <li>{@code bce} — 百度智能云 {@code bce-auth-v1}，两级 {@code HMAC-SHA256-HEX}，
 *       签名头域 {@code host;x-bce-date}（财务等接口必须携带 {@code x-bce-date} 头，
 *       缺失时 IAM 网关直接报 {@code SignatureDateEmpty}，只签 host 已实测不够）；
 *       文档：{@code https://cloud.baidu.com/doc/Reference/s/njwvz1yfu}。</li>
 *   <li>{@code aliyun-bss} — 阿里云 BSS OpenAPI（RPC 风格）老签名，
 *       {@code HMAC-SHA1(SecretKey + "&", StringToSign)} + Base64，签名放 query 的
 *       {@code Signature} 参数；文档：{@code https://help.aliyun.com/zh/user-center/developer-reference/signature}。
 *       注意 BSS 老 API 用的是 {@code SignatureVersion=1.0} 这套，不是百炼那套
 *       {@code ACS3-HMAC-SHA256} V3 签名。</li>
 *   <li>{@code tencent} — 腾讯云 {@code TC3-HMAC-SHA256}，用 {@code "TC3" + SecretKey}
 *       逐级派生签名密钥；Credential 固定四段 {@code <SecretId>/<日期>/<服务>/tc3_request}
 *       （billing 无地域，不写 region 段），签名头域 {@code content-type;host;x-tc-action}，
 *       X-TC-Action 实际头发原样大小写、规范头里用小写；
 *       文档：{@code https://cloud.tencent.com/document/api/213/30654}。</li>
 *   <li>{@code huawei} — 华为云 {@code SDK-HMAC-SHA256}，单层
 *       {@code HMAC-SHA256(SecretKey, StringToSign)}（无密钥派生链），
 *       {@code StringToSign = SDK-HMAC-SHA256 + "\n" + X-Sdk-Date + "\n" + HexEncode(Hash(CanonicalRequest))}，
 *       签名头域 {@code host;x-sdk-date}；
 *       文档：{@code https://support.huaweicloud.com/devg-apisign/api-sign-algorithm-001.html}。</li>
 * </ul>
 *
 * <p>只做签名计算，不发请求、不落库；缺 AK/SK 时由调用方（host 预设）提前拦下并返回
 * 明确提示，不会带着空密钥去发一个必然 401 的请求。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
public final class CreditSigner {

    /**
     * UTC 基本格式（火山 X-Date）：yyyyMMdd'T'HHmmss'Z'
     */
    private static final DateTimeFormatter UTC_BASIC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /**
     * UTC ISO 格式（百度 timestamp / 阿里 Timestamp）：yyyy-MM-dd'T'HH:mm:ss'Z'
     */
    private static final DateTimeFormatter UTC_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /**
     * 百度 / 阿里认证字符串的默认有效期（秒）
     */
    private static final int EXPIRATION_SECONDS = 1800;

    /**
     * 十六进制字符表
     */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CreditSigner() {
    }

    /**
     * 按方案对一次余额请求签名。
     *
     * @param query 余额查询（含 {@code signType} / {@code accessKey} / {@code secretKey}）
     * @param url   预设地址（签名方案会据此补 query 参数）
     * @return 可直接发出的完整请求
     * @throws IllegalStateException 未知签名方案或签名计算失败
     */
    public static Signed sign(CreditQuery query, String url) {
        if (query == null || query.getSignType() == null || query.getSignType().isBlank()) {
            throw new IllegalStateException("未指定签名方案");
        }
        String ak = query.getAccessKey() == null ? "" : query.getAccessKey().trim();
        String sk = query.getSecretKey() == null ? "" : query.getSecretKey().trim();
        if (ak.isEmpty() || sk.isEmpty()) {
            throw new IllegalStateException("该厂商余额接口需要 AK/SK 签名，请先配置 accessKey 与 secretKey");
        }
        String type = query.getSignType().trim().toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "qiniu":
                return qiniu(ak, sk, url);
            case "volc":
                return volc(ak, sk, url);
            case "volc-ark":
                return volcArk(ak, sk, url, query.getBody());
            case "bce":
                return bce(ak, sk, url);
            case "aliyun-bss":
                return aliyunBss(ak, sk, url);
            case "tencent":
                return tencent(ak, sk, url);
            case "huawei":
                return huawei(ak, sk, url);
            default:
                throw new IllegalStateException("未知签名方案: " + type);
        }
    }

    // ==================== 七牛 ====================

    /**
     * 七牛管理凭证签名。
     *
     * <p>{@code signingStr = <Method> <Path>?<Query>\nHost: <Host>\nContent-Type: <ContentType>\n\n<Body>}，
     * 用 SecretKey 做 HMAC-SHA1，URL-safe Base64 后拼成 {@code <AccessKey>:<encodedSign>}。</p>
     *
     * @param ak  AccessKey
     * @param sk  SecretKey
     * @param url 请求地址
     * @return 签名后的请求
     */
    private static Signed qiniu(String ak, String sk, String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = pathOf(uri);
        String query = uri.getRawQuery();
        String pathWithQuery = path + (query == null || query.isEmpty() ? "" : "?" + query);
        String method = "GET";
        // 官方 Java SDK signQiniuAuthorization：GET 且无 Content-Type 时签名串不写 Content-Type 行，
        // 形如 "GET /path?query\nHost: host\n\n"；带 padding 的 URL-safe Base64。
        String signingStr = method + " " + pathWithQuery
                + "\nHost: " + host
                + "\n\n";
        String encodedSign = Base64.getUrlEncoder()
                .encodeToString(hmac("HmacSHA1", sk, signingStr));
        Map<String, String> headers = new LinkedHashMap<>(2);
        headers.put("Authorization", "Qiniu " + ak + ":" + encodedSign);
        return new Signed(url, method, headers, null);
    }

    // ==================== 火山引擎 ====================

    /**
     * 火山引擎 HMAC-SHA256 签名（QueryBalanceAcct）。
     *
     * <p>规范请求 {@code CanonicalRequest} → {@code StringToSign} → 由 SK 逐级派生
     * {@code kDate/kRegion/kService/kSigning} 后算出签名；签名头域固定 {@code host;x-date}。</p>
     *
     * @param ak  AccessKeyId
     * @param sk  SecretAccessKey
     * @param url 请求地址
     * @return 签名后的请求
     */
    private static Signed volc(String ak, String sk, String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = pathOf(uri);
        String method = "GET";
        Instant now = Instant.now();
        String xDate = UTC_BASIC.format(now);
        String shortDate = xDate.substring(0, 8);
        String region = "cn-beijing";
        String service = "billing";
        String version = "2022-01-01";
        String action = "QueryBalanceAcct";

        TreeMap<String, String> params = new TreeMap<>();
        params.put("Action", action);
        params.put("Version", version);
        String canonicalQuery = canonicalQuery(params, CreditSigner::normalize);

        String canonicalHeaders = "host:" + host + "\n" + "x-date:" + xDate + "\n";
        String signedHeaders = "host;x-date";
        String payloadHash = sha256Hex("");
        String canonicalRequest = method + "\n" + path + "\n" + canonicalQuery + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String credentialScope = shortDate + "/" + region + "/" + service + "/request";
        String stringToSign = "HMAC-SHA256\n" + xDate + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] kDate = hmac("HmacSHA256", sk, shortDate);
        byte[] kRegion = hmac("HmacSHA256", kDate, region);
        byte[] kService = hmac("HmacSHA256", kRegion, service);
        byte[] kSigning = hmac("HmacSHA256", kService, "request");
        String signature = hex(hmac("HmacSHA256", kSigning, stringToSign));
        String authorization = "HMAC-SHA256 Credential=" + ak + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>(6);
        headers.put("X-Date", xDate);
        headers.put("X-Content-Sha256", payloadHash);
        headers.put("Authorization", authorization);
        String finalUrl = schemeHost(uri) + path + "?" + canonicalQuery;
        return new Signed(finalUrl, method, headers, null);
    }

    /**
     * 火山方舟 OpenAPI 统一入口签名（{@code POST /?Action=...&Version=...}）。
     *
     * <p>与 {@link #volc(String, String, String)} 同一套 {@code HMAC-SHA256} 逐级派生链，
     * 差异在：{@code service=ark}、{@code region=cn-beijing}、POST + JSON body，
     * 签名头域 {@code content-type;host;x-content-sha256;x-date}（body 摘要参与签名）。
     * {@code Action}/{@code Version} 从统一入口地址的 query 解析后按规范重排，
     * 保证「参与签名的字节」与「实际发出的字节」完全一致。</p>
     *
     * @param ak   AccessKeyId
     * @param sk   SecretAccessKey
     * @param url  统一入口地址（含 Action/Version query）
     * @param body JSON 请求体（空则按 {@code {}} 发送）
     * @return 签名后的请求
     */
    private static Signed volcArk(String ak, String sk, String url, String body) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = pathOf(uri);
        String method = "POST";
        String contentType = "application/json";
        String region = "cn-beijing";
        String service = "ark";

        // Action/Version 已在统一入口地址的 query 里，解析出来按规范重排
        TreeMap<String, String> params = new TreeMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
        }
        String canonicalQuery = canonicalQuery(params, CreditSigner::normalize);

        String payload = body == null || body.isBlank() ? "{}" : body;
        String payloadHash = sha256Hex(payload);
        String xDate = UTC_BASIC.format(Instant.now());
        String shortDate = xDate.substring(0, 8);

        TreeMap<String, String> canonicalHeaders = new TreeMap<>();
        canonicalHeaders.put("content-type", contentType);
        canonicalHeaders.put("host", host);
        canonicalHeaders.put("x-content-sha256", payloadHash);
        canonicalHeaders.put("x-date", xDate);
        StringBuilder headerLines = new StringBuilder();
        for (Map.Entry<String, String> entry : canonicalHeaders.entrySet()) {
            headerLines.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        String signedHeaders = "content-type;host;x-content-sha256;x-date";
        String canonicalRequest = method + "\n" + path + "\n" + canonicalQuery + "\n"
                + headerLines + "\n" + signedHeaders + "\n" + payloadHash;
        String credentialScope = shortDate + "/" + region + "/" + service + "/request";
        String stringToSign = "HMAC-SHA256\n" + xDate + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] kDate = hmac("HmacSHA256", sk, shortDate);
        byte[] kRegion = hmac("HmacSHA256", kDate, region);
        byte[] kService = hmac("HmacSHA256", kRegion, service);
        byte[] kSigning = hmac("HmacSHA256", kService, "request");
        String signature = hex(hmac("HmacSHA256", kSigning, stringToSign));
        String authorization = "HMAC-SHA256 Credential=" + ak + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>(8);
        headers.put("Content-Type", contentType);
        headers.put("X-Date", xDate);
        headers.put("X-Content-Sha256", payloadHash);
        headers.put("Authorization", authorization);
        String finalUrl = schemeHost(uri) + path + "?" + canonicalQuery;
        return new Signed(finalUrl, method, headers, payload);
    }

    // ==================== 百度智能云 ====================

    /**
     * 百度智能云 {@code bce-auth-v1} 签名（POST /v1/finance/cash/balance）。
     *
     * <p>{@code signingKey = HMAC-SHA256-HEX(sk, authStringPrefix)}，
     * {@code signature = HMAC-SHA256-HEX(signingKey, canonicalRequest)}。
     * 认证字符串 {@code bce-auth-v1/<ak>/<timestamp>/<expiration>/<signedHeaders>/<signature>}。
     * 签名头域固定 {@code host;x-bce-date}：财务接口必须携带 {@code x-bce-date} 头
     * （缺失时 IAM 网关报 {@code SignatureDateEmpty}），官方文档明确本接口签名只涉及
     * {@code host} 与 {@code x-bce-date} 两个头域；显式写进 {@code signedHeaders}，
     * 不让底层自动追加的 content-length 等头参与运算。</p>
     *
     * @param ak  AccessKeyId
     * @param sk  SecretAccessKey
     * @param url 请求地址
     * @return 签名后的请求
     */
    private static Signed bce(String ak, String sk, String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = normalizePath(pathOf(uri));
        String method = "POST";
        String contentType = "application/json; charset=utf-8";
        String timestamp = UTC_ISO.format(Instant.now());
        String authStringPrefix = "bce-auth-v1/" + ak + "/" + timestamp + "/" + EXPIRATION_SECONDS;
        String signingKey = hmacHex("HmacSHA256", sk, authStringPrefix);

        String canonicalUri = normalizePath(path);
        String canonicalQuery = "";
        // 头域按字典序排列：host 在前，x-bce-date 在后；值按 RFC 3986 归一化（: → %3A）
        String canonicalHeaders = "host:" + normalize(host) + "\n"
                + "x-bce-date:" + normalize(timestamp);
        String canonicalRequest = method + "\n" + canonicalUri + "\n" + canonicalQuery + "\n"
                + canonicalHeaders;
        String signature = hmacHex("HmacSHA256", signingKey, canonicalRequest);
        String signedHeaders = "host;x-bce-date";
        String authorization = authStringPrefix + "/" + signedHeaders + "/" + signature;

        Map<String, String> headers = new LinkedHashMap<>(8);
        headers.put("Content-Type", contentType);
        headers.put("x-bce-date", timestamp);
        headers.put("Authorization", authorization);
        // POST 带一个空 JSON body：Content-Type 已写进 headers，BCE v1 的规范请求不含 body 摘要，
        // 所以 body 内容不参与签名；给 {} 只是让上游按 JSON POST 正常解析，不影响签名正确性。
        return new Signed(url, method, headers, "{}");
    }

    // ==================== 阿里云 BSS ====================

    /**
     * 阿里云 BSS OpenAPI（RPC 风格）老签名（QueryAccountBalance）。
     *
     * <p>{@code StringToSign = HTTPMethod + "&" + percentEncode("/") + "&" +
     * percentEncode(CanonicalizedQueryString)}，密钥 {@code SecretKey + "&"} 做 HMAC-SHA1，
     * Base64 后作为 query 参数 {@code Signature}。</p>
     *
     * @param ak  AccessKeyId
     * @param sk  AccessKeySecret
     * @param url 请求地址
     * @return 签名后的请求
     */
    private static Signed aliyunBss(String ak, String sk, String url) {
        URI uri = URI.create(url);
        String method = "GET";
        String path = pathOf(uri);
        TreeMap<String, String> params = new TreeMap<>();
        params.put("Action", "QueryAccountBalance");
        params.put("Version", "2017-12-14");
        params.put("AccessKeyId", ak);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Timestamp", UTC_ISO.format(Instant.now()));
        params.put("Format", "JSON");
        String canonicalQuery = canonicalQuery(params, CreditSigner::bssEncode);
        String stringToSign = method + "&" + bssEncode(path) + "&" + bssEncode(canonicalQuery);
        String signature = Base64.getEncoder().encodeToString(
                hmac("HmacSHA1", sk + "&", stringToSign));
        String finalUrl = schemeHost(uri) + path + "?" + canonicalQuery
                + "&Signature=" + bssEncode(signature);
        // Host 由底层执行器按 URL 自动设置；签名里只用了 canonicalQuery，不额外签头
        Map<String, String> headers = new LinkedHashMap<>(1);
        return new Signed(finalUrl, method, headers, null);
    }

    // ==================== 腾讯云 ====================

    /**
     * 腾讯云 {@code TC3-HMAC-SHA256} 签名（billing DescribeAccountBalance）。
     *
     * <p>规范请求 → 待签字符串 → 由 {@code "TC3" + SecretKey} 逐级派生签名密钥
     * {@code kTc3/kService/kSigning} 后算出签名。Credential 固定四段
     * {@code <SecretId>/<日期>/<服务>/tc3_request}：billing 是无地域服务，不写 region 段
     * （多写一段会被网关以 {@code Credential scope size not valid} 拒掉）。签名头域固定
     * {@code content-type;host;x-tc-action}；{@code X-TC-Action} 实际头发原样大小写，
     * 规范头里用小写。文档：{@code https://cloud.tencent.com/document/api/213/30654}。</p>
     *
     * @param ak  SecretId
     * @param sk  SecretKey
     * @param url 请求地址
     * @return 签名后的请求
     */
    private static Signed tencent(String ak, String sk, String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = pathOf(uri);
        String method = "POST";
        String contentType = "application/json; charset=utf-8";
        String service = "billing";
        String action = "DescribeAccountBalance";
        String version = "2018-07-09";
        String body = "{}";
        Instant now = Instant.now();
        long timestamp = now.getEpochSecond();
        String shortDate = UTC_ISO.format(now).substring(0, 10);

        TreeMap<String, String> canonical = new TreeMap<>();
        canonical.put("content-type", contentType);
        canonical.put("host", host);
        canonical.put("x-tc-action", action.toLowerCase(java.util.Locale.ROOT));
        StringBuilder canonicalHeaders = new StringBuilder();
        for (Map.Entry<String, String> entry : canonical.entrySet()) {
            canonicalHeaders.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        String signedHeaders = "content-type;host;x-tc-action";
        String payloadHash = sha256Hex(body);
        String canonicalRequest = method + "\n" + path + "\n\n" + canonicalHeaders + "\n"
                + signedHeaders + "\n" + payloadHash;
        String credentialScope = shortDate + "/" + service + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] kTc3 = hmac("HmacSHA256", "TC3" + sk, shortDate);
        byte[] kService = hmac("HmacSHA256", kTc3, service);
        byte[] kSigning = hmac("HmacSHA256", kService, "tc3_request");
        String signature = hex(hmac("HmacSHA256", kSigning, stringToSign));
        String authorization = "TC3-HMAC-SHA256 Credential=" + ak + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>(8);
        headers.put("Content-Type", contentType);
        headers.put("X-TC-Action", action);
        headers.put("X-TC-Version", version);
        headers.put("X-TC-Timestamp", String.valueOf(timestamp));
        headers.put("Authorization", authorization);
        return new Signed(url, method, headers, body);
    }

    // ==================== 华为云 ====================

    /**
     * 华为云 {@code SDK-HMAC-SHA256} 签名（BSS 客户账户余额）。
     *
     * <p>与 AWS 式派生链不同：签名密钥就是 SecretKey 本身（单层
     * {@code HMAC-SHA256(SecretKey, StringToSign)}，无 kDate 派生），
     * {@code StringToSign = SDK-HMAC-SHA256 + "\n" + X-Sdk-Date + "\n" + HexEncode(Hash(CanonicalRequest))}，
     * 签名头域固定 {@code host;x-sdk-date}。
     * 文档：{@code https://support.huaweicloud.com/devg-apisign/api-sign-algorithm-001.html}。</p>
     *
     * @param ak  AccessKeyId
     * @param sk  SecretAccessKey
     * @param url 请求地址
     * @return 签名后的请求
     */
    private static Signed huawei(String ak, String sk, String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        String path = normalizePath(pathOf(uri));
        String method = "GET";
        String sdkDate = UTC_BASIC.format(Instant.now());
        String canonicalHeaders = "host:" + host + "\n" + "x-sdk-date:" + sdkDate + "\n";
        String signedHeaders = "host;x-sdk-date";
        String payloadHash = sha256Hex("");
        String canonicalRequest = method + "\n" + path + "\n\n" + canonicalHeaders + "\n"
                + signedHeaders + "\n" + payloadHash;
        String stringToSign = "SDK-HMAC-SHA256\n" + sdkDate + "\n" + sha256Hex(canonicalRequest);
        String signature = hmacHex("HmacSHA256", sk, stringToSign);
        String authorization = "SDK-HMAC-SHA256 Access=" + ak
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("X-Sdk-Date", sdkDate);
        headers.put("Authorization", authorization);
        return new Signed(url, method, headers, null);
    }

    // ==================== 公共工具 ====================

    /**
     * 取请求路径，空路径按根 {@code /} 处理。
     *
     * @param uri 地址
     * @return 路径
     */
    private static String pathOf(URI uri) {
        String path = uri.getPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    /**
     * 取 {@code scheme://host[:port]}，用于重拼带签名参数的完整地址。
     *
     * @param uri 地址
     * @return 协议与主机部分
     */
    private static String schemeHost(URI uri) {
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port);
    }

    /**
     * 按参数名排序拼规范查询串，键与值都用指定编码器编码。
     *
     * @param params  参数（TreeMap 已按 key 排序）
     * @param encoder 编码器
     * @return 规范查询串
     */
    private static String canonicalQuery(TreeMap<String, String> params, Function<String, String> encoder) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encoder.apply(entry.getKey())).append('=').append(encoder.apply(entry.getValue()));
        }
        return sb.toString();
    }

    /**
     * application/x-www-form-urlencoded 百分号编码（与真正拼进 URL 的编码一致）。
     *
     * @param value 原值
     * @return 编码值
     */
    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 阿里云 RPC 签名的 percentEncode：在 URLEncoder 基础上把 {@code +} 改 {@code %20}、
     * {@code *} 改 {@code %2A}、{@code %7E} 还原成 {@code ~}。
     *
     * @param value 原值
     * @return 编码值
     */
    private static String bssEncode(String value) {
        return enc(value).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    /**
     * RFC 3986 归一化：只保留 unreserved 字符，其余按大写 {@code %XX} 编码（百度 BCE 用）。
     *
     * @param value 原值
     * @return 归一化值
     */
    private static String normalize(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int v = b & 0xFF;
            if ((v >= 'a' && v <= 'z') || (v >= 'A' && v <= 'Z') || (v >= '0' && v <= '9')
                    || v == '-' || v == '.' || v == '_' || v == '~') {
                sb.append((char) v);
            } else {
                sb.append('%').append(Character.toUpperCase(HEX[v >>> 4]))
                        .append(Character.toUpperCase(HEX[v & 0x0F]));
            }
        }
        return sb.toString();
    }

    /**
     * 归一化路径：段内编码，但保留路径分隔符 {@code /}。
     *
     * @param path 路径
     * @return 归一化路径
     */
    private static String normalizePath(String path) {
        return normalize(path).replace("%2F", "/");
    }

    /**
     * HMAC 摘要（密钥为字符串时按 UTF-8 取字节）。
     *
     * @param algorithm HmacSHA1 / HmacSHA256
     * @param key       密钥
     * @param data      待签数据
     * @return 摘要字节
     */
    private static byte[] hmac(String algorithm, String key, String data) {
        return hmac(algorithm, key.getBytes(StandardCharsets.UTF_8), data);
    }

    /**
     * HMAC 摘要（密钥为字节数组，用于逐级派生签名密钥）。
     *
     * @param algorithm HmacSHA1 / HmacSHA256
     * @param key       密钥字节
     * @param data      待签数据
     * @return 摘要字节
     */
    private static byte[] hmac(String algorithm, byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("签名计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * HMAC 摘要的十六进制字符串。
     *
     * @param algorithm 算法
     * @param key       密钥
     * @param data      待签数据
     * @return 小写十六进制
     */
    private static String hmacHex(String algorithm, String key, String data) {
        return hex(hmac(algorithm, key, data));
    }

    /**
     * SHA-256 摘要的十六进制字符串（规范请求的 payload hash）。
     *
     * @param data 原文
     * @return 小写十六进制
     */
    private static String sha256Hex(String data) {
        return DigestUtils.sha256(data);
    }

    /**
     * 字节转小写十六进制。
     *
     * @param bytes 字节
     * @return 十六进制字符串
     */
    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX[v >>> 4];
            chars[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * 一次签名后的完整请求。
     *
     * <p>{@link #url} 已经是「地址 + 已编码的签名参数」，{@link #headers} 就是最终请求头；
     * 出口直接用它们建请求，不再补 {@code Bearer}、不再二次编码。</p>
     */
    public static final class Signed {

        /**
         * 完整地址
         */
        private final String url;

        /**
         * HTTP 方法
         */
        private final String method;

        /**
         * 最终请求头
         */
        private final Map<String, String> headers;

        /**
         * 请求体（可为空串；null 表示不带 body）
         */
        private final String body;

        /**
         * 构造签名请求。
         *
         * @param url     完整地址
         * @param method  HTTP 方法
         * @param headers 最终请求头
         * @param body    请求体（可为空串；null 表示不带 body）
         */
        private Signed(String url, String method, Map<String, String> headers, String body) {
            this.url = url;
            this.method = method;
            this.headers = headers;
            this.body = body;
        }

        /**
         * 获取完整地址。
         *
         * @return 完整地址
         */
        public String getUrl() {
            return url;
        }

        /**
         * 获取 HTTP 方法。
         *
         * @return HTTP 方法
         */
        public String getMethod() {
            return method;
        }

        /**
         * 获取最终请求头。
         *
         * @return 最终请求头
         */
        public Map<String, String> getHeaders() {
            return headers;
        }

        /**
         * 获取请求体。
         *
         * @return 请求体（可为空串；null 表示不带 body）
         */
        public String getBody() {
            return body;
        }
    }
}
