package com.chua.starter.datasearch.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasearch.pojo.location.LocationVO;
import com.chua.starter.datasearch.service.WeatherDataService;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定位解析接口。
 *
 * <p>顶栏天气的取位置环节：服务端按客户端来源 IP 出经纬度与地名，
 * 省掉浏览器定位授权与三方定位接口。内网部署时退回服务器自身定位。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Tag(name = "定位解析接口")
@RestController
@RequestMapping("/v2/location")
@RequiredArgsConstructor
public class LocationV2Controller {

    /**
     * 天气数据服务
    */
    private final WeatherDataService weatherDataService;

    /**
     * 解析当前请求来源的展示位置
     *
     * @param request 当前请求
     * @return 定位结果
     */
    @Operation(summary = "解析当前请求来源的展示位置")
    @TokenForIgnore
    @GetMapping("/resolve")
    public ReturnResult<LocationVO> resolve(HttpServletRequest request) {
        try {
            LocationVO location = weatherDataService.resolveLocation(request);
            return location == null ? ReturnResult.error("无可用定位数据源") : ReturnResult.ok(location);
        } catch (Exception e) {
            log.warn("[location] 定位解析失败: {}", e.getMessage());
            return ReturnResult.error("定位解析失败：" + e.getMessage());
        }
    }
}
