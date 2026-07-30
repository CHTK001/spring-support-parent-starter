package com.chua.common.support.constant;

/**
 * 通用常量接口
 * <p>
 * 包含系统各处通用的常量定义，
 * 如空字符串、空数组、默认编码、JSON 相关标记符等。
 * </p>
 * @author CH
 */
public interface CommonConstant {

    /**
     * 空字符串常量
     */
    String EMPTY_STRING = "";

    /**
     * 空字节数组
     */
    byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * UTF-8 字符集名称
     */
    String UTF_8 = "UTF-8";

    /**
     * GBK 字符集名称
     */
    String GBK = "GBK";

    /**
     * JSON Content-Type
     */
    String CONTENT_TYPE_JSON = "application/json";

    /**
     * HTTP GET 方法
     */
    String METHOD_GET = "GET";

    /**
     * HTTP POST 方法
     */
    String METHOD_POST = "POST";

    /**
     * 左小括号 '('
     */
    String SYMBOL_LEFT_BIG_PARENTHESES = "(";

    /**
     * 左方括号 '['
     */
    String SYMBOL_LEFT_SQUARE_BRACKET = "[";

    /**
     * 右小括号 ')'
     */
    String SYMBOL_RIGHT_BIG_PARENTHESES = ")";

    /**
     * 右方括号 ']'
     */
    String SYMBOL_RIGHT_SQUARE_BRACKET = "]";

    /**
     * 左花括号 '{'
     */
    String SYMBOL_LEFT_CURLY_BRACES = "{";

    /**
     * 右花括号 '}'
     */
    String SYMBOL_RIGHT_CURLY_BRACES = "}";

    /**
     * 点号
     */
    String SYMBOL_COMMA = ",";

    /**
     * 冒号
     */
    String SYMBOL_COLON = ":";

    /**
     * 等号
     */
    String SYMBOL_EQUALS = "=";

    /**
     * 空串
     */
    String SYMBOL_EMPTY = "";

    /**
     * 空 JSON 对象字符串表示
     */
    String EMPTY_JSON_OBJECT = "{}";

    /**
     * 空 JSON 数组字符串表示
     */
    String EMPTY_JSON_ARRAY = "[]";
}