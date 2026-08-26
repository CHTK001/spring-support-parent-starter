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
 * <p>支持多 Bot 绑定的增删改查与状态更新。</p>
 */
@RestController
@RequestMapping("/v2/ai/bot-binding")
@RequiredArgsConstructor
public class AiBotBindingController {

    private final AiBotBindingService botBindingService;

    @GetMapping("/list")
    public ReturnResult<List<SysAiBotBinding>> list() {
        return ReturnResult.ok(botBindingService.listAll());
    }

    @GetMapping("/{id}")
    public ReturnResult<SysAiBotBinding> get(@PathVariable Long id) {
        return ReturnResult.ok(botBindingService.getById(id));
    }

    @PostMapping("/create")
    public ReturnResult<Long> create(@RequestBody SysAiBotBinding binding) {
        return ReturnResult.ok(botBindingService.saveOrUpdate(binding));
    }

    @PutMapping("/update")
    public ReturnResult<Long> update(@RequestBody SysAiBotBinding binding) {
        return ReturnResult.ok(botBindingService.saveOrUpdate(binding));
    }

    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        botBindingService.delete(id);
        return ReturnResult.ok(null);
    }
}
