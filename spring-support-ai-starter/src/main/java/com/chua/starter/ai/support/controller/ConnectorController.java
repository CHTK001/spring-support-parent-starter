// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.entity.SysAiBotMessage;
import com.chua.starter.ai.support.service.AiBotBindingService;
import com.chua.starter.ai.support.service.AiBotChatService;
import com.chua.starter.ai.support.util.AiBotCaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接器管理控制器。
 *
 * <p>提供连接器类型列表、已绑定列表、绑定、解绑等接口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final AiBotBindingService botBindingService;
    private final AiBotChatService aiBotChatService;

    @GetMapping("/types")
    public ReturnResult<List<Map<String, Object>>> getConnectorTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        
        // 微信 iLink
        Map<String, Object> ilink = new HashMap<>();
        ilink.put("type", "ilink");
        ilink.put("name", "微信");
        ilink.put("description", "通过 iLink 扫码接入微信");
        ilink.put("icon", "💬");
        ilink.put("bindType", "qrcode");
        types.add(ilink);

        // 飞书
        Map<String, Object> feishu = new HashMap<>();
        feishu.put("type", "feishu");
        feishu.put("name", "飞书");
        feishu.put("description", "通过 App ID/Secret 接入飞书机器人");
        feishu.put("icon", "🐦");
        feishu.put("bindType", "form");
        types.add(feishu);

        // 钉钉（开放平台机器人：AppKey/AppSecret，Stream 长连接，无需公网回调地址）
        Map<String, Object> dingtalk = new HashMap<>();
        dingtalk.put("type", "dingtalk");
        dingtalk.put("name", "钉钉");
        dingtalk.put("description", "通过 AppKey/AppSecret 接入钉钉机器人（Stream 长连接）");
        dingtalk.put("icon", "🔔");
        dingtalk.put("bindType", "form");
        types.add(dingtalk);

        // QQ
        Map<String, Object> qq = new HashMap<>();
        qq.put("type", "qq");
        qq.put("name", "QQ");
        qq.put("description", "通过 App ID/Secret 接入 QQ 机器人");
        qq.put("icon", "🐧");
        qq.put("bindType", "form");
        types.add(qq);

        // WhatsApp Cloud API
        Map<String, Object> whatsapp = new HashMap<>();
        whatsapp.put("type", "whatsapp");
        whatsapp.put("name", "WhatsApp");
        whatsapp.put("description", "通过 Meta Cloud API 接入 WhatsApp 商务号（Webhook 收发）");
        whatsapp.put("icon", "📱");
        whatsapp.put("bindType", "form");
        types.add(whatsapp);

        return ReturnResult.success(types);
    }

    @GetMapping("/bound")
    public ReturnResult<List<Map<String, Object>>> getBoundConnectors() {
        List<SysAiBotBinding> bindings = botBindingService.listAll();
        List<Map<String, Object>> result = new ArrayList<>();
        AiBotCaller caller = AiBotCaller.fromLogin();

        for (SysAiBotBinding binding : bindings) {
            // 管理员可见全量，普通用户仅本人归属的连接器
            if (!caller.canRead(binding.getOwnerKey())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", binding.getId());
            item.put("type", binding.getPlatform());
            item.put("name", binding.getNickname());
            item.put("status", binding.getStatus());
            item.put("modelName", binding.getModel());
            item.put("roleId", binding.getRoleId());
            item.put("mentionOnly", Boolean.TRUE.equals(binding.getMentionOnly()));
            item.put("randomReplyEnabled", Boolean.TRUE.equals(binding.getRandomReplyEnabled()));
            item.put("randomContextCount", binding.getRandomContextCount());
            item.put("randomProbability", binding.getRandomProbability());
            item.put("createTime", binding.getCreateTime());
            result.add(item);
        }

        return ReturnResult.success(result);
    }

    @GetMapping("/{id}")
    public ReturnResult<Map<String, Object>> getConnectorById(@PathVariable Long id) {
        SysAiBotBinding binding = botBindingService.getById(id);
        if (binding == null) {
            return ReturnResult.error("连接器不存在");
        }
        AiBotCaller.fromLogin().requireReadable(binding.getOwnerKey());

        Map<String, Object> result = new HashMap<>();
        result.put("id", binding.getId());
        result.put("type", binding.getPlatform());
        result.put("name", binding.getNickname());
        result.put("status", binding.getStatus());
        result.put("modelName", binding.getModel());
        result.put("roleId", binding.getRoleId());
        result.put("createTime", binding.getCreateTime());
        result.put("lastActiveTime", binding.getLastActiveTime());
        result.put("mcpServerIds", binding.getMcpServerIds());

        return ReturnResult.success(result);
    }

    @GetMapping("/{id}/conversations")
    public ReturnResult<List<Map<String, Object>>> getConnectorConversations(@PathVariable Long id) {
        return ReturnResult.success(
                aiBotChatService.listBindingConversations(id, AiBotCaller.fromLogin()));
    }

    /**
     * 获取某会话的消息列表（按时间升序，构成该会话独立上下文）。
     *
     * @param chatId 会话标识（联系人/群 openid）
     */
    @GetMapping("/{id}/messages")
    public ReturnResult<List<Map<String, Object>>> getConnectorMessages(
            @PathVariable Long id,
            @RequestParam String chatId) {
        AiBotCaller caller = AiBotCaller.fromLogin();
        List<SysAiBotMessage> messages = aiBotChatService.listConversationMessages(id, chatId, caller);

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiBotMessage message : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", message.getId());
            item.put("role", "IN".equals(message.getDirection()) ? "user" : "assistant");
            item.put("content", message.getContent());
            item.put("fromUser", message.getFromUser());
            item.put("fromGroup", Boolean.TRUE.equals(message.getFromGroup()));
            item.put("createTime", message.getCreateTime());
            result.add(item);
        }

        return ReturnResult.success(result);
    }

    @DeleteMapping("/{id}/conversations")
    public ReturnResult<Integer> clearConversation(@PathVariable Long id, @RequestParam String chatId) {
        int deleted = aiBotChatService.clearConversation(id, chatId, AiBotCaller.fromLogin());
        return ReturnResult.success(deleted);
    }

    /**
    /**
     * 生成微信 iLink 扫码二维码（真实委托 AiBotBindingService）。
     * @return 返回值
    */
    public ReturnResult<AiBotBindingService.QrResult> getILinkQrcode() {
        AiBotCaller caller = AiBotCaller.fromLogin();
        return ReturnResult.success(botBindingService.generateQrCode("wechat", null, caller.ownerKey()));
    }

    /**
    /**
     * 轮询微信扫码状态：pending / ready / scanned / confirmed / expired / error。
     * @param key 字符串
     * @return 返回值
    */
    public ReturnResult<AiBotBindingService.QrStatus> getILinkQrcodeStatus(@RequestParam String key) {
        return ReturnResult.success(botBindingService.checkQrCodeStatus(key, AiBotCaller.fromLogin().ownerKey()));
    }

    /**
     * 绑定连接器（凭据型：feishu / qq，使用 App ID + App Secret）。
     * <p>校验并写入绑定后，立即纳入 {@link AiBotChatService} 托管运行。</p>
     */
    @PostMapping("/bind")
    public ReturnResult<Long> bindConnector(@RequestBody Map<String, Object> request) {
        String type = request.get("type") == null ? "" : String.valueOf(request.get("type")).trim();
        String appId = request.get("appId") == null ? "" : String.valueOf(request.get("appId")).trim();
        String appSecret = request.get("appSecret") == null ? "" : String.valueOf(request.get("appSecret")).trim();
        String phoneNumberId = request.get("phoneNumberId") == null ? "" : String.valueOf(request.get("phoneNumberId")).trim();
        String accessToken = request.get("accessToken") == null ? "" : String.valueOf(request.get("accessToken")).trim();
        String verifyToken = request.get("verifyToken") == null ? "" : String.valueOf(request.get("verifyToken")).trim();
        String model = request.get("model") == null ? null : String.valueOf(request.get("model")).trim();
        AiBotCaller caller = AiBotCaller.fromLogin();

        Long id;
        switch (type) {
            case "feishu" -> id = botBindingService.bindFeishu(appId, appSecret, model, caller.ownerKey());
            case "qq" -> id = botBindingService.bindQq(appId, appSecret, model, caller.ownerKey());
            case "dingtalk" -> id = botBindingService.bindDingTalk(appId, appSecret, model, caller.ownerKey());
            case "whatsapp" -> id = botBindingService.bindWhatsApp(phoneNumberId, accessToken,
                    appSecret, verifyToken, model, caller.ownerKey());
            default -> {
                return ReturnResult.error("暂不支持该连接器类型的绑定：" + type);
            }
        }
        if (id == null) {
            return ReturnResult.error("绑定失败");
        }
        // 立即纳入托管运行（创建真实客户端并开始监听）
        SysAiBotBinding binding = botBindingService.getById(id);
        if (binding != null) {
            aiBotChatService.startBot(binding);
        }
        log.info("连接器绑定成功 type={} id={}", type, id);
        return ReturnResult.success(id);
    }

    @PostMapping("/{id}/send")
    public ReturnResult<String> sendMessage(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        String chatId = request.get("chatId") == null ? "" : String.valueOf(request.get("chatId")).trim();
        String content = request.get("content") == null ? "" : String.valueOf(request.get("content"));
        if (chatId.isEmpty()) {
            return ReturnResult.error("缺少会话标识 chatId");
        }
        log.info("[Connector] 手动发送 id={} chat={}", id, chatId);
        var result = aiBotChatService.sendToConversation(id, chatId, content, AiBotCaller.fromLogin());
        return result.isSuccess()
                ? ReturnResult.success("发送成功")
                : ReturnResult.error(result.getErrorMessage());
    }

    /**
     * AI 发送：以输入框的 prompt 为当前问题，附带该会话最近 N 条上下文，交给 AI 生成回复并发送。
     *
     * <p>请求体：{@code {"chatId":"会话标识","prompt":"问题","contextCount":10}}
     * （prompt 必填；contextCount 可空，空则用绑定配置的上下文条数）。
     * 返回 AI 回复内容与是否发送成功。</p>
     */
    @PostMapping("/{id}/ai-send")
    public ReturnResult<Map<String, Object>> aiSend(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> request) {
        String chatId = request.get("chatId") == null ? "" : String.valueOf(request.get("chatId")).trim();
        if (chatId.isEmpty()) {
            return ReturnResult.error("缺少会话标识 chatId");
        }
        String prompt = request.get("prompt") == null ? "" : String.valueOf(request.get("prompt")).trim();
        if (prompt.isEmpty()) {
            return ReturnResult.error("请输入要让 AI 处理的内容");
        }
        Integer contextCount = null;
        if (request.get("contextCount") != null) {
            contextCount = Integer.parseInt(String.valueOf(request.get("contextCount")).trim());
        }
        log.info("[Connector] AI 发送 id={} chat={} count={}", id, chatId, contextCount);
        return ReturnResult.success(aiBotChatService.aiSendToConversation(
                id, chatId, prompt, contextCount, AiBotCaller.fromLogin()));
    }

    /**
     * 编辑已绑定连接器（修改展示名称 / 对话模型）。
     */
    @PutMapping("/{id}")
    public ReturnResult<String> updateConnector(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> request) {
        SysAiBotBinding binding = botBindingService.getById(id);
        if (binding == null) {
            return ReturnResult.error("连接器不存在");
        }
        // 编辑属写操作，仅归属本人可改（管理员改他人有效连接器同样受限）
        AiBotCaller.fromLogin().requireWritable(binding.getOwnerKey());

        if (request.containsKey("nickname") && request.get("nickname") != null) {
            binding.setNickname(String.valueOf(request.get("nickname")).trim());
        }
        if (request.containsKey("model")) {
            binding.setModel(request.get("model") == null ? "" : String.valueOf(request.get("model")).trim());
        }
        if (request.containsKey("roleId")) {
            Object roleIdValue = request.get("roleId");
            binding.setRoleId(roleIdValue == null || String.valueOf(roleIdValue).trim().isEmpty()
                    ? null
                    : Integer.parseInt(String.valueOf(roleIdValue).trim()));
        }
        if (request.containsKey("mentionOnly")) {
            binding.setMentionOnly(Boolean.TRUE.equals(request.get("mentionOnly")));
        }
        if (request.containsKey("randomReplyEnabled")) {
            binding.setRandomReplyEnabled(Boolean.TRUE.equals(request.get("randomReplyEnabled")));
        }
        if (request.containsKey("randomContextCount") && request.get("randomContextCount") != null) {
            int count = Integer.parseInt(String.valueOf(request.get("randomContextCount")).trim());
            binding.setRandomContextCount(Math.max(1, Math.min(100, count)));
        }
        if (request.containsKey("randomProbability") && request.get("randomProbability") != null) {
            double probability = Double.parseDouble(String.valueOf(request.get("randomProbability")).trim());
            binding.setRandomProbability(Math.max(0.0, Math.min(100.0, probability)));
        }
        if (request.containsKey("mcpServerIds")) {
            binding.setMcpServerIds(request.get("mcpServerIds") == null
                    ? "" : String.valueOf(request.get("mcpServerIds")).trim());
        }
        botBindingService.updateBinding(binding);
        // 重新加载并重启运行中的 Bot：AiBotChatService.startBot 把传入的 binding 对象捕获进
        // 消息监听器闭包，只 updateBinding 不会刷新微信被动回复使用的模型。
        // 这里先从库重读一次，再 restart，否则消息线程仍会用旧的 connector.modelName。
        SysAiBotBinding refreshed = botBindingService.getById(id);
        if (refreshed != null) {
            aiBotChatService.startBot(refreshed);
        }
        log.info("编辑连接器: id={} model={} mentionOnly={} random={} prob={}", id,
                binding.getModel(), binding.getMentionOnly(), binding.getRandomReplyEnabled(),
                binding.getRandomProbability());
        return ReturnResult.success("保存成功");
    }

    /**
     * 解绑连接器
     */
    @DeleteMapping("/{id}")
    public ReturnResult<String> unbindConnector(@PathVariable Long id) {
        SysAiBotBinding binding = botBindingService.getById(id);
        if (binding == null) {
            return ReturnResult.error("连接器不存在");
        }
        // 解绑会停止机器人消息轮询；本人可删，管理员额外可清理无主或未完成的无效绑定
        AiBotCaller caller = AiBotCaller.fromLogin();
        caller.requireWritableOrAdminPurge(
                binding.getOwnerKey(), binding.getPlatform(), binding.getBotId());
        log.info("解绑连接器: id={}", id);
        botBindingService.delete(id);
        return ReturnResult.success("解绑成功");
    }
}