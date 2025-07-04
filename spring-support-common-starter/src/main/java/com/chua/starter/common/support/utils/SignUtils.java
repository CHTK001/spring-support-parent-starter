package com.chua.starter.common.support.utils;

import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 签名计算工具类
 * <p>提供基于Map参数的签名计算功能，支持多种签名算法</p>
 *
 * @author CH
 * @since 2024/12/04
 */
@Slf4j
public class SignUtils {

    /**
     * 默认的参数连接符
     */
    private static final String DEFAULT_SEPARATOR = "&";

    /**
     * 默认的键值连接符
     */
    private static final String DEFAULT_KEY_VALUE_SEPARATOR = "=";

    /**
     * 需要排除的参数名称
     */
    private static final Set<String> DEFAULT_EXCLUDE_KEYS = Set.of("sign", "signature");

    /**
     * 根据Map参数计算MD5签名
     * <p>按照key的字典序排序，拼接成字符串后计算MD5</p>
     *
     * @param params 参数Map
     * @return MD5签名字符串
     */
    public static String calculateMd5Sign(Map<String, Object> params) {
        return calculateMd5Sign(params, null);
    }

    /**
     * 根据Map参数计算MD5签名
     * <p>按照key的字典序排序，拼接成字符串后计算MD5</p>
     *
     * @param params    参数Map
     * @param secretKey 密钥（可选，会追加到参数字符串末尾）
     * @return MD5签名字符串
     */
    public static String calculateMd5Sign(Map<String, Object> params, String secretKey) {
        String paramString = buildSortedParamString(params);
        if (StringUtils.isNotEmpty(secretKey)) {
            paramString += secretKey;
        }
        return DigestUtils.md5Hex(paramString);
    }

    /**
     * 根据Map参数计算SHA256签名
     *
     * @param params 参数Map
     * @return SHA256签名字符串
     */
    public static String calculateSha256Sign(Map<String, Object> params) {
        return calculateSha256Sign(params, null);
    }

    /**
     * 根据Map参数计算SHA256签名
     *
     * @param params    参数Map
     * @param secretKey 密钥（可选，会追加到参数字符串末尾）
     * @return SHA256签名字符串
     */
    public static String calculateSha256Sign(Map<String, Object> params, String secretKey) {
        String paramString = buildSortedParamString(params);
        if (StringUtils.isNotEmpty(secretKey)) {
            paramString += secretKey;
        }
        return DigestUtils.sha256Hex(paramString);
    }

    /**
     * 验证MD5签名
     *
     * @param params       参数Map
     * @param expectedSign 期望的签名值
     * @return 验证结果
     */
    public static boolean verifyMd5Sign(Map<String, Object> params, String expectedSign) {
        return verifyMd5Sign(params, expectedSign, null);
    }

    /**
     * 验证MD5签名
     *
     * @param params       参数Map
     * @param expectedSign 期望的签名值
     * @param secretKey    密钥
     * @return 验证结果
     */
    public static boolean verifyMd5Sign(Map<String, Object> params, String expectedSign, String secretKey) {
        if (StringUtils.isEmpty(expectedSign)) {
            return false;
        }
        String actualSign = calculateMd5Sign(params, secretKey);
        return expectedSign.equalsIgnoreCase(actualSign);
    }

    /**
     * 验证SHA256签名
     *
     * @param params       参数Map
     * @param expectedSign 期望的签名值
     * @return 验证结果
     */
    public static boolean verifySha256Sign(Map<String, Object> params, String expectedSign) {
        return verifySha256Sign(params, expectedSign, null);
    }

    /**
     * 验证SHA256签名
     *
     * @param params       参数Map
     * @param expectedSign 期望的签名值
     * @param secretKey    密钥
     * @return 验证结果
     */
    public static boolean verifySha256Sign(Map<String, Object> params, String expectedSign, String secretKey) {
        if (StringUtils.isEmpty(expectedSign)) {
            return false;
        }
        String actualSign = calculateSha256Sign(params, secretKey);
        return expectedSign.equalsIgnoreCase(actualSign);
    }

    /**
     * 构建排序后的参数字符串
     * <p>按照key的字典序排序，格式：key1=value1&key2=value2</p>
     *
     * @param params 参数Map
     * @return 排序后的参数字符串
     */
    public static String buildSortedParamString(Map<String, Object> params) {
        return buildSortedParamString(params, DEFAULT_EXCLUDE_KEYS);
    }

    /**
     * 构建排序后的参数字符串
     *
     * @param params      参数Map
     * @param excludeKeys 需要排除的key集合
     * @return 排序后的参数字符串
     */
    public static String buildSortedParamString(Map<String, Object> params, Set<String> excludeKeys) {
        return buildSortedParamString(params, excludeKeys, DEFAULT_SEPARATOR, DEFAULT_KEY_VALUE_SEPARATOR);
    }

    /**
     * 构建排序后的参数字符串
     *
     * @param params            参数Map
     * @param excludeKeys       需要排除的key集合
     * @param separator         参数间的分隔符
     * @param keyValueSeparator 键值对的分隔符
     * @return 排序后的参数字符串
     */
    public static String buildSortedParamString(Map<String, Object> params,
                                                Set<String> excludeKeys,
                                                String separator,
                                                String keyValueSeparator) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        Set<String> excludeSet = excludeKeys != null ? excludeKeys : Collections.emptySet();
        String sep = separator != null ? separator : DEFAULT_SEPARATOR;
        String kvSep = keyValueSeparator != null ? keyValueSeparator : DEFAULT_KEY_VALUE_SEPARATOR;

        return params.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .filter(entry -> !excludeSet.contains(entry.getKey().toLowerCase()))
                .filter(entry -> StringUtils.isNotEmpty(String.valueOf(entry.getValue())))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + kvSep + entry.getValue())
                .collect(Collectors.joining(sep));
    }

    /**
     * 构建签名参数Map（包含签名字段）
     *
     * @param params    原始参数Map
     * @param secretKey 密钥
     * @return 包含签名的参数Map
     */
    public static Map<String, Object> buildSignedParams(Map<String, Object> params, String secretKey) {
        Map<String, Object> signedParams = new HashMap<>(params);
        String sign = calculateMd5Sign(params, secretKey);
        signedParams.put("sign", sign);
        return signedParams;
    }

    /**
     * 构建签名参数Map（包含签名字段）
     *
     * @param params    原始参数Map
     * @param secretKey 密钥
     * @param signKey   签名字段名称
     * @return 包含签名的参数Map
     */
    public static Map<String, Object> buildSignedParams(Map<String, Object> params, String secretKey, String signKey) {
        Map<String, Object> signedParams = new HashMap<>(params);
        String sign = calculateMd5Sign(params, secretKey);
        signedParams.put(signKey, sign);
        return signedParams;
    }

    /**
     * 构建签名参数Map（使用SHA256算法）
     *
     * @param params    原始参数Map
     * @param secretKey 密钥
     * @return 包含签名的参数Map
     */
    public static Map<String, Object> buildSha256SignedParams(Map<String, Object> params, String secretKey) {
        Map<String, Object> signedParams = new HashMap<>(params);
        String sign = calculateSha256Sign(params, secretKey);
        signedParams.put("sign", sign);
        return signedParams;
    }

    /**
     * 从参数Map中提取并验证签名
     *
     * @param params    包含签名的参数Map
     * @param secretKey 密钥
     * @return 验证结果
     */
    public static boolean extractAndVerifySign(Map<String, Object> params, String secretKey) {
        return extractAndVerifySign(params, secretKey, "sign");
    }

    /**
     * 从参数Map中提取并验证签名
     *
     * @param params    包含签名的参数Map
     * @param secretKey 密钥
     * @param signKey   签名字段名称
     * @return 验证结果
     */
    public static boolean extractAndVerifySign(Map<String, Object> params, String secretKey, String signKey) {
        if (params == null || !params.containsKey(signKey)) {
            log.warn("签名参数不存在: {}", signKey);
            return false;
        }

        String expectedSign = String.valueOf(params.get(signKey));
        Map<String, Object> paramsWithoutSign = new HashMap<>(params);
        paramsWithoutSign.remove(signKey);

        boolean result = verifyMd5Sign(paramsWithoutSign, expectedSign, secretKey);
        if (!result) {
            log.warn("签名验证失败，期望: {}, 实际: {}", expectedSign, calculateMd5Sign(paramsWithoutSign, secretKey));
        }
        return result;
    }

    /**
     * 打印调试信息
     *
     * @param params    参数Map
     * @param secretKey 密钥
     */
    public static void debugSign(Map<String, Object> params, String secretKey) {
        String paramString = buildSortedParamString(params);
        String fullString = StringUtils.isNotEmpty(secretKey) ? paramString + secretKey : paramString;
        String sign = DigestUtils.md5Hex(fullString);

        log.debug("=== 签名调试信息 ===");
        log.debug("原始参数: {}", params);
        log.debug("排序后参数字符串: {}", paramString);
        log.debug("完整签名字符串: {}", fullString);
        log.debug("MD5签名: {}", sign);
        log.debug("==================");
    }
}
