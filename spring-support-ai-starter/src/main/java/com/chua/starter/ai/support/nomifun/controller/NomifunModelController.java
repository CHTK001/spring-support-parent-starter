package com.chua.starter.ai.support.nomifun.controller;

import com.chua.starter.ai.support.nomifun.service.NomifunModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * nomifun 契约 - 提供商模型接口（轻量壳）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/provider-models")
@RequiredArgsConstructor
public class NomifunModelController {

    private final NomifunModelService modelService;

    /** 列出模型 */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String provider_id) {
        return modelService.listProviderModels(provider_id);
    }

    /** 保存/更新模型 */
    @PutMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        return modelService.saveProviderModel(body);
    }

    /** 删除模型 */
    @DeleteMapping
    public void delete(@RequestParam String provider_id, @RequestParam String model) {
        modelService.deleteProviderModel(provider_id, model);
    }
}
