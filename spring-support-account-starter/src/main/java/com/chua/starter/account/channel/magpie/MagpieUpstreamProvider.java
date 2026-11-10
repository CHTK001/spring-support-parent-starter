// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelUpstreamProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * magpie 上游 provider 的字段适配。
 *
 * <p>magpie 的 provider 模型对象是它自己的内部结构（不是 OpenAI 的 model 形态）：
 * 价格在 {@code list.input} / {@code list.output}（USD / 百万 token），
 * 免费是 {@code free=true}（订阅）或 {@code zero=true}（价格全 0），
 * 倍率是 {@code rate}（0.29 = 0.29 倍）。这些差异属于 magpie 内部实现，
 * 不能扩散到 AI 模块的控制器里，因此按 {@link ChannelUpstreamProvider} 收在本实现：
 * 控制器只拿标准键。</p>
 *
 * <p><b>缺值不补</b>：上游没给价格 / 免费 / 倍率时，这里不返回对应键。页面上
 * 「免费」与「不知道价格」必须是两种状态，不能把未知写成 0。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Spi(MagpieSupport.CHANNEL)
public class MagpieUpstreamProvider implements ChannelUpstreamProvider {

    /**
     * 上下文窗口候选键。
     */
    private static final String[] CONTEXT_KEYS = {
            "contextWindow", "context_window", "context_length", "contextWindowTokens",
            "maxContext", "context"
    };

    /**
     * 免费标记候选键。
     *
     * <p>magpie 对 OpenRouter 的 {@code :free} 模型给的是 {@code zero=true}
     * （价格全 0），对订阅类模型给的是 {@code free=true}，两种都表示免费。</p>
     */
    private static final String[] FREE_KEYS = {"free", "zero", "isFree", "is_free"};


    /**
     * 输入价格候选键（每百万 token）。
     */
    private static final String[] INPUT_PRICE_KEYS = {
            "priceInput", "inputPrice", "price_input", "input_price",
            "promptPrice", "prompt_price", "price_in", "inputPricePerMillion"
    };

    /**
     * 输出价格候选键（每百万 token）。
     */
    private static final String[] OUTPUT_PRICE_KEYS = {
            "priceOutput", "outputPrice", "price_output", "output_price",
            "completionPrice", "completion_price", "price_out", "outputPricePerMillion"
    };

    /**
     * 倍率候选键。
     */
    private static final String[] RATE_KEYS = {"rate", "ratio", "multiplier", "倍率"};

    /**
     * {@inheritDoc}
     */
    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> modelMeta(Map<String, Object> model) {
        Map<String, Object> out = new LinkedHashMap<>(6);
        if (model == null || model.isEmpty()) {
            return out;
        }
        Long contextWindow = asLong(pick(model, CONTEXT_KEYS));
        if (contextWindow != null) {
            out.put("contextWindow", contextWindow);
        }
        Boolean free = asBoolean(pick(model, FREE_KEYS));
        if (free != null) {
            out.put("free", free);
        }
        Double priceInput = asDouble(pick(model, INPUT_PRICE_KEYS));
        if (priceInput == null) {
            priceInput = listPrice(model, "input");
        }
        if (priceInput != null) {
            out.put("priceInput", priceInput);
        }
        Double priceOutput = asDouble(pick(model, OUTPUT_PRICE_KEYS));
        if (priceOutput == null) {
            priceOutput = listPrice(model, "output");
        }
        if (priceOutput != null) {
            out.put("priceOutput", priceOutput);
        }
        Double rate = asDouble(pick(model, RATE_KEYS));
        if (rate != null) {
            out.put("rate", rate);
        }
        // 只给了价格、没给免费标记：两个价格都为 0 才认定为免费，
        // 只缺一个价格不能推断（缺的那项可能收费）。
        if (free == null && priceInput != null && priceOutput != null
                && priceInput == 0D && priceOutput == 0D) {
            out.put("free", Boolean.TRUE);
        }
        return out;
    }

    /**
     * magpie 的价格放在 {@code list} 子对象里：
     * {@code {input, output, cache_read, cache_write}}，单位 USD / 百万 token。
     *
     * <p>只在没有平铺价格键时回落到这里；缺键返回 {@code null}，不补 0。</p>
     *
     * @param model magpie 模型对象
     * @param key   {@code input} / {@code output}
     * @return 单价；没有返回 {@code null}
     */
    private static Double listPrice(Map<String, Object> model, String key) {
        Object list = model.get("list");
        if (!(list instanceof Map<?, ?> prices)) {
            return null;
        }
        return asDouble(prices.get(key));
    }

    /**
     * 按候选键取值：先精确匹配，再忽略大小写找一次。
     *
     * @param source 上游模型对象
     * @param keys   候选键
     * @return 第一个非空值；都没有返回 {@code null}
     */
    private static Object pick(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String name = entry.getKey();
            for (String key : keys) {
                if (key.equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 转为整数（支持数字 / 数字字符串 / 带千分位字符串）。
     *
     * @param value 原始值
     * @return 整数；不可解析返回 {@code null}
     */
    private static Long asLong(Object value) {
        Double number = asDouble(value);
        return number == null ? null : Math.round(number);
    }

    /**
     * 转为浮点数。
     *
     * @param value 原始值
     * @return 浮点数；不可解析返回 {@code null}
     */
    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim().replace(",", "")
                .replace("$", "").replace("¥", "").replace("￥", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 转为布尔值。
     *
     * @param value 原始值（布尔 / 字符串 / 数字）
     * @return 布尔值；无法判断返回 {@code null}
     */
    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
