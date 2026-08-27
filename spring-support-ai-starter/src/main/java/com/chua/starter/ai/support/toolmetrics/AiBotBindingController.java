package com.chua.starter.ai.support.toolmetrics;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.BotBindingCreateRequest;
import com.chua.starter.ai.support.model.BotBindingListResponse;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.service.AiBotBindingService;
import lombok.RequiredArgsConstructor;
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
        resp.setId(entity.getSysAiBotBindingId());
        resp.setBotId(entity.getSysAiBotBindingBotId());
        resp.setPlatform(entity.getSysAiBotBindingPlatform());
        resp.setNickname(entity.getSysAiBotBindingNickname());
        resp.setAvatarUrl(entity.getSysAiBotBindingAvatarUrl());
        resp.setGatewayPort(entity.getSysAiBotBindingGatewayPort());
        resp.setStatus(entity.getSysAiBotBindingStatus());
        resp.setBindSource(entity.getSysAiBotBindingBindSource());
        resp.setCreateTime(entity.getCreateTime());
        resp.setLastActiveTime(entity.getSysAiBotBindingLastActiveTime());
        return resp;
    }
}
