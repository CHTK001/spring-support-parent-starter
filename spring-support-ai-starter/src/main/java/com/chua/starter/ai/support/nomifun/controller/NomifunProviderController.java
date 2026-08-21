package com.chua.starter.ai.support.nomifun.controller;

import com.chua.starter.ai.support.nomifun.service.NomifunProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * nomifun 契约 - 提供商接口（适配现有 AiManufacturerFacadeService）。
 *
 * <p>前端调用 /api/providers/*，本控制器映射到后端 {@link NomifunProviderService}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class NomifunProviderController {

    private final NomifunProviderService providerService;

    /** 列出所有提供商（返回 PaginatedResult 格式） */
    @GetMapping
    public Map<String, Object> list() {
        var items = providerService.listProviders();
        return Map.of("items", items, "total", items.size(), "has_more", false);
    }

    /** 创建提供商 */
    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Map.of();
        Map<String, Object> result = providerService.createProvider(body);
        if (result == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "create failed");
        return result;
    }

    /** 获取单个提供商 */
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable("id") String id) {
        Map<String, Object> result = providerService.getProvider(id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "provider not found: " + id);
        return result;
    }

    /** 更新提供商 */
    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable("id") String id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Map.of();
        Map<String, Object> result = providerService.updateProvider(id, body);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "provider not found: " + id);
        return result;
    }

    /** 删除提供商 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") String id) {
        boolean ok = providerService.deleteProvider(id);
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "provider not found: " + id);
    }

    /** 克隆提供商 */
    @PostMapping("/{id}/clone")
    public Map<String, Object> clone(@PathVariable("id") String id) {
        Map<String, Object> result = providerService.cloneProvider(id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "provider not found: " + id);
        return result;
    }
}
