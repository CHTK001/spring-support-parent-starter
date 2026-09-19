package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.service.AiBotBindingService;
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

        for (SysAiBotBinding binding : bindings) {
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
     * 解绑连接器
     */
    @DeleteMapping("/{id}")
    public ReturnResult<String> unbindConnector(@PathVariable Long id) {
        log.info("解绑连接器: id={}", id);
        // TODO: 调用 AiBotBindingService 的解绑方法
        return ReturnResult.success("解绑成功");
    }
}
