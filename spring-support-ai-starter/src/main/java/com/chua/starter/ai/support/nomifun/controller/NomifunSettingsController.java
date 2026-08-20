package com.chua.starter.ai.support.nomifun.controller;

import com.chua.starter.ai.support.nomifun.service.NomifunContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Nomifun 契约 - 设置接口（轻量壳）。
 *
 * <p>映射 nomifun 前端 {@code configService} / 设置命令到
 * {@link NomifunContractService}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class NomifunSettingsController {

    /**
     * 契约业务服务
     */
    private final NomifunContractService contractService;

    /**
     * 读取客户端设置。
     *
     * @return 客户端设置对象
     */
    @GetMapping("/client")
    public Map<String, Object> client() {
        return contractService.clientSettings();
    }

    /**
     * 全量覆盖客户端设置。
     *
     * @param body 前端提交的完整设置对象
     */
    @PutMapping("/client")
    public void updateClient(@RequestBody Map<String, Object> body) {
        contractService.updateClientSettings(body);
    }
}
