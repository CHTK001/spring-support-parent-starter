package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PPIO 派欧云余额实现。
 *
 * <p>官方计费域与模型网关域不同主域：模型网关是 {@code api.ppinfra.com}，余额在计费域
 * {@code GET https://api.ppio.com/openapi/v1/billing/balance/detail}（Bearer 鉴权，
 * 模型侧同一把渠道 Key 可查）。因为跨主域，登记表条目的 sameOrigin 兜底既拦不住也放不
 * 进来，这里按专用实现处理：地址缺省取官方计费域，供应商行也可用 {@code extra.credit_url}
 * 覆盖成完整余额地址。</p>
 *
 * <p>响应为根级字符串字段（单位 <b>1/10000 元</b>，即 10000 = 1 元）：{@code availableBalance}
 * （可用余额）、{@code cashBalance}（现金余额）、{@code creditLimit}（授信额度）、
 * {@code pendingCharges}（待出账金额）、{@code outstandingInvoices}（未结账单）等。
 * 按「可用余额 / 现金余额」两段读数返回，其余字段折算成元后进 extra；字段缺失按
 * 「接口结构可能已变更」如实报错，不编造读数。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("ppio")
public class PpioCreditQueryProvider implements CreditQueryProvider {

    /**
     * 实现标识
     */
    static final String NAME = "ppio";

    /**
     * 官方余额接口（计费域 api.ppio.com，与模型网关 api.ppinfra.com 不同主域）
     */
    private static final String DEFAULT_URL = "https://api.ppio.com/openapi/v1/billing/balance/detail";

    /**
     * 币种：人民币
     */
    private static final String CURRENCY = "CNY";

    /**
     * 金额单位换算：响应金额单位为 1/10000 元
     */
    private static final int SCALE = 4;

    /**
     * 响应映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 查 PPIO 账户余额。
     *
     * <p>鉴权为渠道 Key 的 Bearer（CreditHttp 对 Authorization 头默认补 Bearer 前缀）；
     * 地址缺省取官方计费域余额接口，供应商行 {@code extra.credit_url} 配了完整地址时优先。</p>
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            if (blank(query.getToken())) {
                return CreditResult.fail(NAME, "缺少 PPIO 渠道 Key：请在渠道凭据或「余额密钥」里配置后重试");
            }
            String url = blank(query.getUrl()) ? DEFAULT_URL : query.getUrl().trim();
            String body = CreditHttp.fetch(query, url);
            JsonNode root = MAPPER.readTree(body);
            // 官方口径为根级字段；若结构变化包了一层 data，也兼容取一层
            JsonNode data = root;
            if (!root.hasNonNull("availableBalance") && !root.hasNonNull("cashBalance")
                    && root.path("data").isObject()) {
                data = root.path("data");
            }
            List<BalancePart> parts = new ArrayList<>(2);
            Map<String, Object> extra = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>(1);
            parseBalance(data, parts, extra, errors);
            if (parts.isEmpty()) {
                return CreditResult.fail(NAME, String.join("；", errors));
            }
            CreditResult result = CreditResult.ok(NAME, parts, body);
            result.setExtra(extra);
            return result;
        } catch (RuntimeException | IOException e) {
            return CreditResult.fail(NAME, e.getMessage());
        }
    }

    /**
     * 解析余额响应：{@code availableBalance} / {@code cashBalance} 两段读数（1/10000 元
     * 折算成元），{@code creditLimit} 等其余金额字段进 extra。
     *
     * @param data   余额字段所在节点（根级或 data 包裹）
     * @param parts  读数出口
     * @param extra  附加数据出口
     * @param errors 错误出口
     */
    void parseBalance(JsonNode data, List<BalancePart> parts,
                      Map<String, Object> extra, List<String> errors) {
        if (!data.isObject()) {
            errors.add("PPIO 余额响应不是 JSON 对象");
            return;
        }
        BigDecimal available = moneyOf(data.path("availableBalance"));
        BigDecimal cash = moneyOf(data.path("cashBalance"));
        if (available == null && cash == null) {
            errors.add("PPIO 余额响应缺少 availableBalance / cashBalance 字段（接口结构可能已变更）");
            return;
        }
        if (available != null) {
            parts.add(part("可用余额", available));
            extra.put("available_balance", available);
        }
        if (cash != null) {
            parts.add(part("现金余额", cash));
            extra.put("cash_balance", cash);
        }
        putMoney(extra, "credit_limit", data.path("creditLimit"));
        putMoney(extra, "pending_charges", data.path("pendingCharges"));
        putMoney(extra, "outstanding_invoices", data.path("outstandingInvoices"));
    }

    /**
     * 金额字段进 extra：原始值（1/10000 元）+ 折算后的元值（字段缺失时跳过）。
     *
     * @param extra 附加数据出口
     * @param key   字段名（产出 {@code key_raw} 与 {@code key_yuan} 两个键）
     * @param node  响应里的金额节点
     */
    private static void putMoney(Map<String, Object> extra, String key, JsonNode node) {
        BigDecimal raw = moneyOf(node);
        if (raw == null) {
            return;
        }
        extra.put(key + "_raw", raw);
        extra.put(key + "_yuan", raw.movePointLeft(SCALE));
    }

    /**
     * 金额节点 → 元：字符串 / 数字节点按 1/10000 元折算（PPIO 金额以字符串返回）。
     *
     * @param node 金额节点
     * @return 元值；节点缺失或不是合法数字为 null
     */
    private static BigDecimal moneyOf(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText().trim()).movePointLeft(SCALE);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 组一段读数（元）。
     *
     * @param label  段名
     * @param amount 金额
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount);
        part.setCurrency(CURRENCY);
        return part;
    }

    /**
     * 空判断。
     *
     * @param text 文本
     * @return 为空为 true
     */
    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
