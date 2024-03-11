package com.chua.starter.common.support.external;

import com.chua.common.support.external.Weather;
import com.chua.common.support.external.WeatherResolver;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.value.Value;
import com.chua.starter.common.support.properties.ExternalInterfaceProperties;
import com.chua.starter.common.support.utils.RequestUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import static com.chua.starter.common.support.constant.Constant.REDIS_CACHE_HOUR;
import static java.util.concurrent.TimeUnit.HOURS;

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
    private static final Cache<String, Value<Weather>> CACHE = CacheBuilder.newBuilder().expireAfterWrite(1, HOURS).build();

    public ExternalController(ExternalInterfaceProperties externalInterfaceProperties) {
        this.externalInterfaceProperties = externalInterfaceProperties;
    }


    /**
     * 天气
     *
     * @param request 城市
     * @return {@link ReturnResult}<{@link Weather}>
     */
    @GetMapping("weather/address")
    @Cacheable(cacheManager = REDIS_CACHE_HOUR, cacheNames = REDIS_CACHE_HOUR, key = "'weather:' + #{(T(com.chua.starter.common.support.utils.RequestUtils).getIpAddress(#request))}")
    public ReturnResult<Weather> getWeather(HttpServletRequest request) {
        String ipAddress = RequestUtils.getIpAddress(request);
        Value<Weather> ifPresent = CACHE.getIfPresent(ipAddress);
        if(null != ifPresent) {
            return ReturnResult.of(ifPresent.getValue());
        }
        Weather weather = WeatherResolver.newDefault().getWeather(ipAddress);
        CACHE.put(ipAddress, Value.of(weather));
        return ReturnResult.of(weather);
    }
    /**
     * 天气
     *
     * @param city 城市
     * @return {@link ReturnResult}<{@link Weather}>
     */
    @GetMapping("weather")
    @Cacheable(cacheManager = REDIS_CACHE_HOUR, cacheNames = REDIS_CACHE_HOUR, key = "'weather:'+#p0")
    public ReturnResult<Weather> getWeather(@RequestParam(value = "city", required = false) String city) {
        city = city == null ? "" : city;
        Value<Weather> ifPresent = CACHE.getIfPresent(city);
        if(null != ifPresent) {
            return ReturnResult.of(ifPresent.getValue());
        }
        Weather weather = WeatherResolver.newDefault().getWeather(city);
        CACHE.put(city, Value.of(weather));
        return ReturnResult.of(weather);
    }
}
