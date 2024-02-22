package com.chua.starter.common.support.external;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.Weather;
import com.chua.starter.common.support.properties.ExternalInterfaceProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部控制器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/22
 */
@RestController
@RequestMapping("/v1/external")
public class ExternalController {
    private final ExternalInterfaceProperties externalInterfaceProperties;

    public ExternalController(ExternalInterfaceProperties externalInterfaceProperties) {
        this.externalInterfaceProperties = externalInterfaceProperties;
    }


    /**
     * 天气
     *
     * @param city 城市
     * @return {@link ReturnResult}<{@link Weather}>
     */
    @GetMapping("weather")
    public ReturnResult<Weather> getWeather(@RequestParam(value = "city", required = false) String city) {
        return ReturnResult.of(Weather.create(city));
    }
}
