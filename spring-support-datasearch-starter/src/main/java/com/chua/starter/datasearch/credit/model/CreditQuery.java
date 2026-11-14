package com.chua.starter.datasearch.credit.model;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 余额查询请求。
 *
 * <p>渠道 / 上游页面里「一个可查余额的来源」被归一成这一份入参：地址、方法、令牌、
 * 字段表达式全部来自我们自己的库，不依赖 magpie 的 provider 配置也能查。</p>
 *
 * <p>{@link #kind} 决定用哪个 {@code CreditQueryProvider} 实现（expression / new-api /
 * sub2api / openai / host）；留空时按「表达式是否齐全」回落到通用实现。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Data
public class CreditQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实现标识（expression / new-api / sub2api / openai / host），留空时按表达式回落
     */
    private String kind;

    /**
     * 上游标识（deepseek / moonshot / openrouter 等），host 实现据此取默认地址与表达式
     */
    private String providerId;

    /**
     * 请求地址；new-api / sub2api / openai 实现允许只给站点根地址，由实现补路径
     */
    private String url;

    /**
     * HTTP 方法（GET / POST / PUT），默认 GET
     */
    private String method;

    /**
     * 令牌明文（落库前由调用方加密，查询前由调用方解密）
     */
    private String token;

    /**
     * 令牌请求头名，空时按实现取默认值（一般为 Authorization）
     */
    private String tokenHeader;

    /**
     * 字段表达式，如 {@code $data.total_available / 500000}、{@code ¥balance}、{@code $a; $b}
     */
    private String expression;

    /**
     * 计费币种口径（usd / cny），空时由表达式币种前缀决定
     */
    private String billing;

    /**
     * 自定义请求体（JSON 字符串），为空时按 {@link #method} 与 {@link #params} 组装
     */
    private String body;

    /**
     * 请求参数：GET 拼查询串，POST/PUT 作为表单提交
     */
    private Map<String, String> params;

    /**
     * 额外请求头
     */
    private Map<String, String> headers;

    /**
     * 追加一个请求参数。
     *
     * @param key   参数名
     * @param value 参数值
     * @return 当前对象，便于链式组装
     */
    public CreditQuery param(String key, String value) {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        params.put(key, value);
        return this;
    }

    /**
     * 追加一个请求头。
     *
     * @param key   头名
     * @param value 头值
     * @return 当前对象，便于链式组装
     */
    public CreditQuery header(String key, String value) {
        if (headers == null) {
            headers = new LinkedHashMap<>();
        }
        headers.put(key, value);
        return this;
    }
}
