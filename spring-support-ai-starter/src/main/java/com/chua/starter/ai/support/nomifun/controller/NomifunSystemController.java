package com.chua.starter.ai.support.nomifun.controller;

import com.chua.starter.ai.support.nomifun.service.NomifunContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Nomifun 契约 - 系统接口（轻量壳）。
 *
 * <p>映射 nomifun 前端 {@code application.*} 命令到 {@link NomifunContractService}。
 * 本控制器只做协议翻译，不承载业务逻辑。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class NomifunSystemController {

    /**
     * 契约业务服务
     */
    private final NomifunContractService contractService;

    /**
     * 系统信息（前端启动必需，缺失即崩溃）。
     *
     * @return 系统信息对象
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return contractService.systemInfo();
    }

    /**
     * 修改工作目录（前端设置页调用，桌面壳模式忽略，保持兼容）。
     *
     * @param body 请求体 {@code { work_dir: string }}
     */
    @PostMapping("/work-dir")
    public void updateWorkDir(@RequestBody(required = false) Map<String, Object> body) {
        log.debug("[nomifun] update work-dir: {}", body);
    }

    /**
     * 恢复出厂设置（前端「重置」调用，当前为占位，不执行危险操作）。
     */
    @PostMapping("/factory-reset")
    public void factoryReset() {
        log.warn("[nomifun] factory-reset invoked (no-op placeholder)");
    }
}
