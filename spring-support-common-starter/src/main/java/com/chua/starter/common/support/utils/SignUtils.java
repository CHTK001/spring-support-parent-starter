package com.chua.starter.common.support.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import com.chua.common.support.utils.StringUtils;

/**
* 签名计算工具类（Spring 侧门面）
*
* <p>本类作为 {@link com.chua.common.support.utils.SignUtils} 的薄封装，
* 保留原有公共 API 不变，内部全部委托给核心工具类实现，
* 避免重复的 MD5/SHA256 计算逻辑。</p>
*
* @see com.chua.common.support.utils.SignUtils
* @author CH
* @since 4.0.0
 */
public class SignUtils {

    /** 日志 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SignUtils.class);

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

    /** 创建 标志工具 实例 */
    private SignUtils() {
    }

    // ==================== MD5 签名 ====================

    /**
    * 根据映射参数计算MD5签名
    * <p>按照key的字典序排序，拼接成字符串后计算MD5</p>
    *
    * @return MD5签名字符串
    * @param params 参数
    */
    public static String calculateMd5Sign(Map<String, Object> params) {
        return calculateMd5Sign(params, null);
    }

    /**
    * 根据映射参数计算MD5签名
    * <p>按照key的字典序排序，拼接成字符串后计算MD5</p>
    *
    * @param secretKey 密钥（可选，会追加到参数字符串末尾）
    * @return MD5签名字符串
    * @param params 参数
    */
    public static String calculateMd5Sign(Map<String, Object> params, String secretKey) {
        return com.chua.common.support.utils.SignUtils.signDirectKey(params, secretKey,
                DEFAULT_KEY_VALUE_SEPARATOR, DEFAULT_SEPARATOR, DEFAULT_EXCLUDE_KEYS);
    }

    // ==================== SHA256 签名 ====================

    /**
    * 根据映射参数计算SHA256签名
    *
    * @return SHA256签名字符串
    * @param params 参数
    */
    public static String calculateSha256Sign(Map<String, Object> params) {
        return calculateSha256Sign(params, null);
    }

    /**
    * 根据映射参数计算SHA256签名
    *
    * @param secretKey 密钥（可选，会追加到参数字符串末尾）
    * @return SHA256签名字符串
    * @param params 参数
    */
    public static String calculateSha256Sign(Map<String, Object> params, String secretKey) {
        return com.chua.common.support.utils.SignUtils.signSha256(params, secretKey,
                DEFAULT_KEY_VALUE_SEPARATOR, DEFAULT_SEPARATOR, DEFAULT_EXCLUDE_KEYS);
    }

 // ==================== MD5 签名（json对象 兼容） ====================

    /**
    * 根据json对象参数生成MD5签名。
    *
    * @param jsonObject JSON对象参数
    * @return MD5签名字符串
    */
    public static String generateSignFromMap(com.chua.common.support.lang.json.JsonObject jsonObject) {
        return calculateMd5Sign(jsonObject);
    }

    // ==================== 签名验证 ====================

    /**
    * 验证MD5签名
    *
    * @param expectedSign 期望的签名值
    * @return 验证结果
    * @param params 参数
    */
    public static boolean verifyMd5Sign(Map<String, Object> params, String expectedSign) {
        return verifyMd5Sign(params, expectedSign, null);
    }

    /**
    * 验证MD5签名
    *
    * @param expectedSign 期望的签名值
    * @param secretKey    密钥
    * @return 验证结果
    * @param params 参数
    */
    public static boolean verifyMd5Sign(Map<String, Object> params, String expectedSign, String secretKey) {
        if (StringUtils.isEmpty(expectedSign)) {
            return false;
        }
        return com.chua.common.support.utils.SignUtils.verifyDirectKey(params, expectedSign, secretKey, DEFAULT_EXCLUDE_KEYS);
    }

    /**
    * 验证SHA256签名
    *
    * @param expectedSign 期望的签名值
    * @return 验证结果
    * @param params 参数
    */
    public static boolean verifySha256Sign(Map<String, Object> params, String expectedSign) {
        return verifySha256Sign(params, expectedSign, null);
    }

    /**
    * 验证SHA256签名
    *
    * @param expectedSign 期望的签名值
    * @param secretKey    密钥
    * @return 验证结果
    * @param params 参数
    */
    public static boolean verifySha256Sign(Map<String, Object> params, String expectedSign, String secretKey) {
        if (StringUtils.isEmpty(expectedSign)) {
            return false;
        }
        return com.chua.common.support.utils.SignUtils.verifySha256(params, expectedSign, secretKey, DEFAULT_EXCLUDE_KEYS);
    }

    // ==================== 构建排序参数字符串 ====================

    /**
    * 构建排序后的参数字符串
    * <p>按照key的字典序排序，格式：key1=value1&key2=value2</p>
    *
    * @return 排序后的参数字符串
    * @param params 参数
    */
    public static String buildSortedParamString(Map<String, Object> params) {
        return buildSortedParamString(params, DEFAULT_EXCLUDE_KEYS);
    }

    /**
    * 构建排序后的参数字符串
    *
    * @param excludeKeys 需要排除的钥匙集合
    * @return 排序后的参数字符串
    * @param params 参数
    */
    public static String buildSortedParamString(Map<String, Object> params, Set<String> excludeKeys) {
        return buildSortedParamString(params, excludeKeys, DEFAULT_SEPARATOR, DEFAULT_KEY_VALUE_SEPARATOR);
    }

    /**
    * 构建排序后的参数字符串
    *
    * @param excludeKeys       需要排除的钥匙集合
    * @param keyValueSeparator 键值对的分隔符
    * @return 排序后的参数字符串
    */
    public static String buildSortedParamString(Map<String, Object> params,
                                                Set<String> excludeKeys,
                                                String separator,
                                                String keyValueSeparator) {
        return com.chua.common.support.utils.SignUtils.buildSortedParamString(params, excludeKeys, separator, keyValueSeparator);
    }

 // ==================== 构建签名参数 映射 ====================

    /**
    * 构建签名参数映射（包含签名字段）
    *
    * @param params    原始参数映射
    * @param secretKey 密钥
    * @return 包含签名的参数Map
    */
    public static Map<String, Object> buildSignedParams(Map<String, Object> params, String secretKey) {
        return com.chua.common.support.utils.SignUtils.buildSignedParams(params, secretKey);
    }

    /**
    * 构建签名参数映射（包含签名字段）
    *
    * @param params    原始参数映射
    * @param secretKey 密钥
    * @param signKey   签名字段名称
    * @return 包含签名的参数Map
    */
    public static Map<String, Object> buildSignedParams(Map<String, Object> params, String secretKey, String signKey) {
        return com.chua.common.support.utils.SignUtils.buildSignedParams(params, secretKey, signKey);
    }

    /**
    * 构建签名参数映射（使用SHA256算法）
    *
    * @param params    原始参数映射
    * @param secretKey 密钥
    * @return 包含签名的参数Map
    */
    public static Map<String, Object> buildSha256SignedParams(Map<String, Object> params, String secretKey) {
        return com.chua.common.support.utils.SignUtils.buildSha256SignedParams(params, secretKey);
    }

    // ==================== 提取并验证签名 ====================

    /**
    * 从参数映射中提取并验证签名
    *
    * @param params    包含签名的参数映射
    * @param secretKey 密钥
    * @return 验证结果
    */
    public static boolean extractAndVerifySign(Map<String, Object> params, String secretKey) {
        return extractAndVerifySign(params, secretKey, "sign");
    }

    /**
    * 从参数映射中提取并验证签名
    *
    * @param params    包含签名的参数映射
    * @param secretKey 密钥
    * @param signKey   签名字段名称
    * @return 验证结果
    */
    public static boolean extractAndVerifySign(Map<String, Object> params, String secretKey, String signKey) {
        if (params == null || !params.containsKey(signKey)) {
            log.warn("签名参数不存在： {}", signKey);
            return false;
        }

        String expectedSign = String.valueOf(params.get(signKey));
        Map<String, Object> paramsWithoutSign = new java.util.HashMap<>(params);
        paramsWithoutSign.remove(signKey);

        boolean result = verifyMd5Sign(paramsWithoutSign, expectedSign, secretKey);
        if (!result) {
            log.warn("签名验证失败，期望： {}, 实际: {}", expectedSign, calculateMd5Sign(paramsWithoutSign, secretKey));
        }
        return result;
    }

    // ==================== 调试 ====================

    /**
    *
    * @param secretKey 密钥
    * @param params 参数
    */
    public static void debugSign(Map<String, Object> params, String secretKey) {
        String paramString = buildSortedParamString(params);
        String fullString = StringUtils.isNotEmpty(secretKey) ? paramString + secretKey : paramString;
        String sign = calculateMd5Sign(params, secretKey);

        log.debug("=== 签名调试信息 ===");
        log.debug("原始参数: {}", params);
        log.debug("排序后参数字符串: {}", paramString);
        log.debug("完整签名字符串 {}", fullString);
        log.debug("MD5签名: {}", sign);
        log.debug("==================");
    }
}
