package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.starter.account.channel.service.AccountChannelChatClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 账号渠道路由适配器，凭据、归属和模型可用性均由账号模块解释。
 * @author CH
 */
@Component
@RequiredArgsConstructor
public class AccountChannelRouteSource implements ChannelRouteSource {
    /** 账号模块对话供给契约。 */
    private final AccountChannelChatClientService clients;
    @Override
    public String name() { return "account"; }
    @Override
    public int order() { return 0; }
    @Override
    public Map<String, RouteTarget> routes(String ownerScope) { return clients.routes(ownerScope); }
}
