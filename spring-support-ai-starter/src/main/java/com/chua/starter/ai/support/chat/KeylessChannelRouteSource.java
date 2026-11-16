// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.channel.KeylessChatChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 免登录对话渠道路由源：DuckDuckGo / Trae 等没有账号、没有上游地址的渠道。
 *
 * <p>这类渠道的协议与会话由 {@code ChatClient} SPI 自己持有（{@code provider} 就是扩展名），
 * 没有 baseUrl，也没有连接可聚合。它们的模型同样是「渠道管理里已开启的模型」，
 * 必须并进免费渠道，否则免费渠道的模型清单里永远看不到这些渠道。</p>
 *
 * <p><b>为什么不是直接依赖账号模块</b>：渠道的完整实现（{@code ChatChannelProvider}）
 * 住在账号模块，而 AI 模块按设计不反向依赖它。common-starter 的
 * {@link KeylessChatChannel} 只暴露路由需要的四项事实（渠道标识 / 扩展名 / 展示名 / 模型），
 * 由账号模块的渠道实现同时实现。</p>
 *
 * <p><b>只注册模型键</b>：渠道标识（{@code chat-duckduckgo}）不是可选模型，
 * 混进清单会让页面出现一个叫 {@code chat-duckduckgo} 的「模型」。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Component
public class KeylessChannelRouteSource implements ChannelRouteSource {

    @Override
    public String name() {
        return "keyless";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean fallbackOnly() {
        // 和真实上游撞名时必须让位：有 baseUrl + 账号的渠道优先级更高
        return true;
    }

    @Override
    public Map<String, RouteTarget> routes(String ownerScope) {
        Map<String, RouteTarget> routes = new LinkedHashMap<>();
        Map<String, KeylessChatChannel> channels;
        try {
            channels = ServiceProvider.of(KeylessChatChannel.class).list();
        } catch (Exception e) {
            log.debug("[KeylessRoute] 免登录对话渠道未接入: {}", e.getMessage());
            return routes;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (KeylessChatChannel channel : channels.values()) {
            if (channel == null || StringUtils.isBlank(channel.channel())
                    || StringUtils.isBlank(channel.extension()) || !seen.add(channel.channel())) {
                continue;
            }
            RouteTarget target = RouteTarget.builder()
                    .provider(channel.extension())
                    .baseUrl("")
                    .authScheme("none")
                    .build();
            int registered = 0;
            try {
                List<Map<String, Object>> models = channel.listModels();
                if (models != null) {
                    for (Map<String, Object> model : models) {
                        Object id = model == null ? null : model.get("id");
                        if (id == null || StringUtils.isBlank(String.valueOf(id))) {
                            continue;
                        }
                        routes.putIfAbsent(String.valueOf(id), target);
                        registered++;
                    }
                }
            } catch (Exception e) {
                // 登录态缺失时模型表可能读不到：跳过该渠道，不影响其它渠道
                log.debug("[KeylessRoute] 渠道 {} 模型清单读取失败: {}", channel.channel(), e.getMessage());
            }
            log.info("[KeylessRoute] 渠道 {} 注册 {} 个模型", channel.channel(), registered);
        }
        return routes;
    }
}
