package com.chua.starter.datasearch.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasearch.pojo.ip.IpLookupVO;
import com.chua.starter.datasearch.service.IpLookupDataService;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IP 归属地接口。
 *
 * <p>前端 {@code ReIpText} 在缺少归属地文案时才调用本接口（{@code thirdPartyFix}），
 * 因此：</p>
 * <ul>
 *   <li>参数可选：HEAD 探测不带查询串也要命中映射；</li>
 *   <li>匿名可访问：IP 提示可能出现在登录前的操作日志/会话详情里；</li>
 *   <li>IP 缺失或非法返回失败码，由调用方跳过展示，不写「未知」误导用户。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Tag(name = "IP 归属地接口")
@RestController
@RequestMapping("/v2/ip")
@RequiredArgsConstructor
public class IpLookupV2Controller {

    /**
     * IP 归属地数据服务
     */
    private final IpLookupDataService ipLookupDataService;

    /**
     * 查询 IP 归属地
     *
     * @param ip IP 地址（支持 {@code 1.2.3.4:8080}，端口会被忽略）
     * @return 归属地
     */
    @Operation(summary = "IP 归属地")
    @Parameter(name = "ip", description = "IP 地址，如 114.114.114.114")
    @TokenForIgnore
    @GetMapping("/lookup")
    public ReturnResult<IpLookupVO> lookup(
            @RequestParam(value = "ip", required = false) String ip) {
        try {
            IpLookupVO result = ipLookupDataService.lookup(ip);
            return result == null
                    ? ReturnResult.error("无可用 IP 归属地数据源：" + (ip == null ? "" : ip))
                    : ReturnResult.ok(result);
        } catch (Exception e) {
            log.warn("[ip-lookup] 归属地查询失败: ip={}, msg={}", ip, e.getMessage());
            return ReturnResult.error("归属地查询失败：" + e.getMessage());
        }
    }
}
