// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.account.manage.service.SysAccountCreditConfigService;
import com.chua.starter.common.support.channel.ChannelUpstreamProvider;
import com.chua.starter.common.support.channel.MagpieSupport;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.support.CreditQueries;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * magpie 上游的「我们自己的余额配置」来源。
 *
 * <p>网关对部分上游没有余额接口，额度卡上只剩「读不到」。宿主可以在
 * 「上游余额配置」里填地址与字段表达式，本实现按 provider id 取那条配置，
 * 交给 datasearch 的 {@code CreditQueryProvider} 去查：</p>
 * <ul>
 *   <li>没有配置或配置停用：返回 {@code null} 表示不接管，保持网关原文；</li>
 *   <li>查到：把读数摊成 {@code packages}，页面上每个字段一行；</li>
 *   <li>查不到：返回 {@code error}，页面显示这次失败的原因。</li>
 * </ul>
 *
 * <p><b>为什么从容器里取服务</b>：SPI 实现由 SPI 容器无参构造，不是 Spring Bean；
 * 配置服务是普通 Bean（含 Mapper），按 {@code SpringBeanUtils} 取一次即可，
 * 不必依赖 SPI 装配器是否已挂上。</p>
 *
 * @author CH
 * @since 4.0.0.82
 */
@Spi("magpie-credit-db")
public class MagpieDbCreditProvider implements ChannelUpstreamProvider {

    /**
     * 单段读数没有名字时的展示名。
     */
    private static final String DEFAULT_PART_NAME = "余额";

    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
    }

    /**
     * 只有配了「能查的」配置才接管这个上游。
     *
     * @param providerId 网关上游 id
     * @return true 表示由本实现查余额
     */
    @Override
    public boolean supports(String providerId) {
        SysAccountCreditConfigService service = service();
        return service != null && service.queryOf(providerId) != null;
    }

    /**
     * 按我们的配置查一次余额。
     *
     * @param providerId  网关上游 id
     * @param account     上游账号（本实现不区分账号，可空）
     * @param credentials 网关连接凭据（本实现不使用）
     * @return 积分快照；没配置 / 查不到配置时返回 {@code null}
     */
    @Override
    public Map<String, Object> credit(String providerId, String account,
                                      Map<String, Object> credentials) {
        SysAccountCreditConfigService service = service();
        if (service == null) {
            return null;
        }
        CreditQuery query = service.queryOf(providerId);
        if (query == null) {
            return null;
        }
        return snapshot(CreditQueries.query(query));
    }

    /**
     * 取容器里的配置服务。
     *
     * @return 服务；容器未就绪或未装配时返回 {@code null}
     */
    private static SysAccountCreditConfigService service() {
        try {
            ApplicationContext context = SpringBeanUtils.getApplicationContextOrNull();
            return context == null ? null : context.getBean(SysAccountCreditConfigService.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 把查询结果摊成额度卡认识的快照。
     *
     * <p>用 {@code packages} 逐段给出，页面上每一段单独一行；多币种、可用 / 赠送
     * 这类并列口径都能原样显示。没有取到数字时只给 {@code error}，让页面显示原因。</p>
     *
     * @param result 查询结果
     * @return 快照
     */
    static Map<String, Object> snapshot(CreditResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>(8);
        snapshot.put("updatedAt", System.currentTimeMillis());
        if (result == null) {
            snapshot.put("error", "余额查询没有返回结果");
            return snapshot;
        }
        if (result.getError() != null) {
            snapshot.put("error", result.getError());
            return snapshot;
        }
        List<Map<String, Object>> packages = new ArrayList<>();
        for (BalancePart part : result.getParts()) {
            if (part == null || part.getAmount() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(4);
            String label = part.getLabel() == null ? "" : part.getLabel().trim();
            row.put("name", label.isEmpty() ? DEFAULT_PART_NAME : label);
            row.put("remain", part.getAmount());
            row.put("currency", part.getCurrency());
            packages.add(row);
        }
        if (packages.isEmpty()) {
            snapshot.put("error", "余额查询没有取到数字，请检查字段表达式");
            return snapshot;
        }
        snapshot.put("packages", packages);
        snapshot.put("remain", result.getAmount());
        snapshot.put("currency", result.getCurrency());
        return snapshot;
    }
}
