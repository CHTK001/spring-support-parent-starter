package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.DiscoveryQueryVO;
import com.chua.starter.monitor.support.model.DiscoveryStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务发现回退控制器。
 *
 * <p>{@code spring-support-discovery-starter} 是可选依赖，普通监控 fat jar 不会携带它。
 * 此时上游 {@link com.chua.starter.monitor.support.controller.ServerDiscoveryController}
 * 因 {@code @ConditionalOnClass} 不注册，前端仍会请求三个 discovery 端点并收到 404。
 * 本控制器在这些路径下返回「未启用」的空状态，让页面按设计降级而不是报网络错误。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Tag(name = "监控-服务发现", description = "服务发现未启用时的回退接口")
@RestController
@ConditionalOnMissingClass("com.chua.starter.discovery.support.service.DiscoveryService")
@RequestMapping("/ms/monitor/discovery")
public class DiscoveryFallbackController {

    /**
     * 未启用时的协议列表。
     *
     * @return 空列表
     */
    @Operation(summary = "启用的发现协议（未启用回退）")
    @GetMapping("/protocols")
    public ReturnResult<List<String>> protocols() {
        return ReturnResult.ok(List.of());
    }

    /**
     * 未启用时的实例查询。
     *
     * @param protocol 协议名（回退时忽略）
     * @param uriSpec  服务标识（回退时忽略）
     * @return 空实例 + 未启用说明
     */
    @Operation(summary = "查询服务实例（未启用回退）")
    @GetMapping("/services")
    public ReturnResult<DiscoveryQueryVO> services(@RequestParam(required = false) String protocol,
                                                   @RequestParam(required = false) String uriSpec) {
        return ReturnResult.ok(new DiscoveryQueryVO(List.of(), "服务发现能力未启用，请部署并启用 discovery-starter"));
    }

    /**
     * 未启用时的状态摘要。
     *
     * @return enabled=false 的状态
     */
    @Operation(summary = "发现服务状态（未启用回退）")
    @GetMapping("/status")
    public ReturnResult<DiscoveryStatusVO> status() {
        return ReturnResult.ok(new DiscoveryStatusVO(0, List.of(), false, false));
    }
}