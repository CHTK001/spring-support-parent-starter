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

    /**
     * 获取所有可用的连接器类型（从 SPI BotClient 获取）
     */
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

        // 企业微信
        Map<String, Object> wecom = new HashMap<>();
        wecom.put("type", "wecom");
        wecom.put("name", "企业微信");
        wecom.put("description", "通过 CorpID/Secret 接入企业微信");
        wecom.put("icon", "💼");
        wecom.put("bindType", "form");
        types.add(wecom);

        return ReturnResult.success(types);
    }

    /**
     * 获取已绑定的连接器列表
     */
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
            item.put("createTime", binding.getCreateTime());
            result.add(item);
        }

        return ReturnResult.success(result);
    }

    /**
     * 获取单个连接器详情
     */
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
        result.put("createTime", binding.getCreateTime());
        result.put("lastActiveTime", binding.getLastActiveTime());

        return ReturnResult.success(result);
    }

    /**
     * 获取连接器消息列表
     */
    @GetMapping("/{id}/messages")
    public ReturnResult<List<Map<String, Object>>> getConnectorMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") Integer limit) {
        SysAiBotBinding binding = botBindingService.getById(id);
        if (binding == null) {
            return ReturnResult.error("连接器不存在");
        }
        AiBotCaller caller = AiBotCaller.fromLogin();
        caller.requireReadable(binding.getOwnerKey());

        List<SysAiBotMessage> messages = aiBotChatService.listMessages(
                binding.getBotId(), binding.getNickname(), caller);
        
        // 只返回最近的 limit 条
        int fromIndex = Math.max(0, messages.size() - limit);
        List<SysAiBotMessage> recentMessages = messages.subList(fromIndex, messages.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiBotMessage message : recentMessages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", message.getId());
            item.put("role", "IN".equals(message.getDirection()) ? "user" : "assistant");
            item.put("content", message.getContent());
            item.put("createTime", message.getCreateTime());
            result.add(item);
        }

        return ReturnResult.success(result);
    }

    /**
     * 获取微信二维码
     */
    @PostMapping("/ilink/qrcode")
    public ReturnResult<Map<String, Object>> getILinkQrcode() {
        Map<String, Object> result = new HashMap<>();
        result.put("qrcodeUrl", "/api/ilink/qrcode/" + System.currentTimeMillis());
        result.put("countdown", 60);
        return ReturnResult.success(result);
    }

    /**
     * 绑定连接器
     */
    @PostMapping("/bind")
    public ReturnResult<String> bindConnector(@RequestBody Map<String, Object> request) {
        String type = (String) request.get("type");
        log.info("绑定连接器: type={}", type);
        // TODO: 调用 AiBotBindingService 的绑定方法
        return ReturnResult.success("绑定成功");
    }

    /**
     * 发送消息到连接器
     */
    @PostMapping("/{id}/send")
    public ReturnResult<String> sendMessage(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        log.info("发送消息到连接器: id={}, content={}", id, content);
        // TODO: 调用 AiBotChatService 发送消息
        return ReturnResult.success("发送成功");
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
        // 解绑会停止他人 机器人 的消息轮询，写出口只认归属
        AiBotCaller.fromLogin().requireWritable(binding.getOwnerKey());
        log.info("解绑连接器: id={}", id);
        botBindingService.delete(id);
        return ReturnResult.success("解绑成功");
    }
}
