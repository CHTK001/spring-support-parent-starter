package com.chua.starter.ai.support.toolmetrics;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.service.AiBotBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 微信 iLink Bot 多绑定管理控制器。
 *
* <p>支持对 Bot 绑定的增删改查、扫描绑定（微信）、状态更新。</p>
 */
@RestController
@RequestMapping("/v2/ai/bot-binding")
@RequiredArgsConstructor
public class AiBotBindingController {

    /**
     * Bot 绑定服务
     */
    private final AiBotBindingService botBindingService;

    /**
     * 获取全部绑定列表。
     *
     * @return 绑定列表（按创建时间降序）
     */
    @GetMapping("/list")
    public ReturnResult<List<SysAiBotBinding>> list() {
        return ReturnResult.ok(botBindingService.listAll());
    }

    /**
     * 按 ID 获取绑定详情。
     *
     * @param id 绑定 ID
     * @return 绑定信息
     */
    @GetMapping("/{id}")
    public ReturnResult<SysAiBotBinding> get(@PathVariable Long id) {
        return ReturnResult.ok(botBindingService.getById(id));
    }

    /**
     * 创建绑定。
     *
     * @param binding 绑定信息
     * @return 数据库 ID
     */
    @PostMapping("/create")
    public ReturnResult<Long> create(@RequestBody SysAiBotBinding binding) {
        return ReturnResult.ok(botBindingService.saveOrUpdate(binding));
    }

    /**
     * 更新绑定。
     *
     * @param binding 绑定信息（含 ID）
     * @return 数据库 ID
     */
    @PutMapping("/update")
    public ReturnResult<Long> update(@RequestBody SysAiBotBinding binding) {
        return ReturnResult.ok(botBindingService.saveOrUpdate(binding));
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

    // ==================== iLink 扫码绑定 ====================

    /**
     * 生成 iLink 扫码会话。
     *
     * <p>平台仅支持 {@code wechat}；企业微信暂不支持扫码，会返回 400。</p>
     *
     * @param platform 平台类型，默认 wechat
     * @return key + qrcodeUrl；qrcodeUrl 可能为空（iLink 尚未返回），前端轮询 status
     */
    @PostMapping("/qrcode")
    public ReturnResult<AiBotBindingService.QrResult> generateQr(
            @RequestParam(defaultValue = "wechat") String platform) {
        if (!"wechat".equals(platform) && !"wecom".equals(platform)) {
            return ReturnResult.illegal("platform 仅支持 wechat / wecom");
        }
        if ("wecom".equals(platform)) {
            return ReturnResult.illegal("企业微信暂不支持扫码绑定，请使用手动导入");
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
}
