package com.chua.starter.ai.support.toolmetrics;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.BotBindingCreateRequest;
import com.chua.starter.ai.support.model.BotBindingListResponse;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.service.AiBotBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 微信 iLink Bot 多绑定管理控制器。
 *
 * <p>支持多 Bot 绑定的增删改查。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/bot-binding")
@RequiredArgsConstructor
public class AiBotBindingController {

    /**
     * Bot 绑定服务
     */
    private final AiBotBindingService botBindingService;

    /**
     * AI 对话客户端注册中心（获取可用模型列表）
     */
    private final com.chua.starter.ai.support.registry.ChatClientRegistry chatClientRegistry;

    /**
     * 获取可用于 Bot 的 AI 模型列表（供前端设置 Bot 的聊天模型）。
     *
     * <p>遍历所有已注册的 ChatClient，聚合各客户端的模型名，去重返回。</p>
     *
     * @return 模型名列表
     */
    @GetMapping("/models")
    public ReturnResult<List<String>> models() {
        java.util.Set<String> models = new java.util.LinkedHashSet<>();
        for (com.chua.common.support.ai.chat.ChatClient client : chatClientRegistry.getAll()) {
            if (client == null) {
                continue;
            }
            try {
                models.addAll(client.listModels());
            } catch (Exception e) {
                // 单个客户端模型列举失败不影响整体
                log.debug("[Bot] 获取客户端模型列表失败: {}", e.getMessage());
            }
        }
        return ReturnResult.ok(new java.util.ArrayList<>(models));
    }

    /**
     * 获取全部绑定列表。
     *
     * @return 绑定列表（按创建时间降序）
     */
    @GetMapping("/list")
    public ReturnResult<List<BotBindingListResponse>> list() {
        List<SysAiBotBinding> entities = botBindingService.listAll();
        List<BotBindingListResponse> result = entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ReturnResult.ok(result);
    }

    /**
     * 按 ID 获取绑定详情。
     *
     * @param id 绑定 ID
     * @return 绑定信息
     */
    @GetMapping("/{id}")
    public ReturnResult<BotBindingListResponse> get(@PathVariable Long id) {
        SysAiBotBinding entity = botBindingService.getById(id);
        return ReturnResult.ok(entity != null ? toResponse(entity) : null);
    }

    /**
     * 创建绑定。
     *
     * @param request 绑定请求
     * @return 数据库 ID
     */
    @PostMapping("/create")
    public ReturnResult<Long> create(@RequestBody BotBindingCreateRequest request) {
        SysAiBotBinding entity = new SysAiBotBinding();
        BeanUtils.copyProperties(request, entity);
        return ReturnResult.ok(botBindingService.saveOrUpdate(entity));
    }

    /**
     * 更新绑定。
     *
     * @param request 绑定请求（含 ID）
     * @return 数据库 ID
     */
    @PutMapping("/update")
    public ReturnResult<Long> update(@RequestBody BotBindingCreateRequest request) {
        SysAiBotBinding entity = new SysAiBotBinding();
        BeanUtils.copyProperties(request, entity);
        return ReturnResult.ok(botBindingService.saveOrUpdate(entity));
    }

    /**
     * 删除绑定。
     *
     * @param id 数据库 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        botBindingService.delete(id);
        return ReturnResult.ok(null);
    }

    /**
     * 实体转响应 DTO。
     *
     * @param entity 数据库实体
     * @return 响应 DTO
     */
    private BotBindingListResponse toResponse(SysAiBotBinding entity) {
        BotBindingListResponse resp = new BotBindingListResponse();
        resp.setId(entity.getId());
        resp.setBotId(entity.getBotId());
        resp.setPlatform(entity.getPlatform());
        resp.setNickname(entity.getNickname());
        resp.setAvatarUrl(entity.getAvatarUrl());
        resp.setGatewayPort(entity.getGatewayPort());
        resp.setStatus(entity.getStatus());
        resp.setBindSource(entity.getBindSource());
        resp.setModel(entity.getModel());
        resp.setCreateTime(entity.getCreateTime());
        resp.setLastActiveTime(entity.getLastActiveTime());
        return resp;
    }

    // ==================== iLink 扫码绑定 ====================

    /**
     * 生成微信 iLink 扫码会话。
     *
     * <p>平台仅支持 wechat（iLink 扫码）；企业微信暂不支持，返回错误。</p>
     *
     * @param platform 平台类型，默认 wechat
     * @return key + qrcodeUrl；qrcodeUrl 即时为空，前端轮询 status=ready 后从 QrStatus 取
     */
    @PostMapping("/qrcode")
    public ReturnResult<AiBotBindingService.QrResult> generateQr(
            @RequestParam(defaultValue = "wechat") String platform) {
        if (!"wechat".equals(platform) && !"wecom".equals(platform)) {
            return ReturnResult.illegal("platform 仅支持 wechat / wecom");
        }
        return ReturnResult.ok(botBindingService.generateQrCode(platform));
    }

    /**
     * 轮询扫码会话状态。
     *
     * <p>前端每 3 秒调用一次；status 变为 confirmed 时表示绑定已完成，可刷新列表。</p>
     *
     * @param key 会话 key
     * @return status + botId（confirmed 时回填）
     */
    @GetMapping("/qrcode/status")
    public ReturnResult<AiBotBindingService.QrStatus> checkQrStatus(@RequestParam String key) {
        return ReturnResult.ok(botBindingService.checkQrCodeStatus(key));
    }

    // ==================== 飞书（凭据配置型） ====================

    /**
     * 绑定飞书 Bot。
     *
     * <p>凭据配置型：传入飞书开放平台的 appId / appSecret。</p>
     *
     * @param appId     飞书应用 ID
     * @param appSecret 飞书应用密钥
     * @return 数据库 ID
     */
    @PostMapping("/feishu")
    public ReturnResult<Long> bindFeishu(@RequestParam String appId,
                                         @RequestParam String appSecret) {
        return ReturnResult.ok(botBindingService.bindFeishu(appId, appSecret));
    }
}
