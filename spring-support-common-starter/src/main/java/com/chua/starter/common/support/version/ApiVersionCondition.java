package com.chua.starter.common.support.version;


import com.chua.starter.common.support.annotations.ApiPlatform;
import com.chua.starter.common.support.annotations.ApiVersion;
import com.chua.starter.common.support.properties.VersionProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API version condition
 *
 * @author ch
 * @since 2020-11-16
 */
@Slf4j
public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {
    /**
     * 接口路径中的版本号前缀，如: api/v[1-n]/test
     */
    private final static Pattern VERSION_PREFIX_PATTERN = Pattern.compile("/v([0-9]+\\.{0,1}[0-9]{0,2})/");

    /**
     * API VERSION interface
     **/
    @Getter
    private ApiVersion apiVersion;
    @Getter
    private ApiPlatform apiPlatform;
    private final VersionProperties versionProperties;

    ApiVersionCondition(ApiVersion apiVersion, ApiPlatform apiPlatform, VersionProperties versionProperties) {
        this.apiVersion = apiVersion;
        this.apiPlatform = apiPlatform;
        this.versionProperties = versionProperties;
    }

    /**
     * [当class 和 method 请求url相同时，触发此方法用于合并url]
     * 官方解释：
     * - 某个接口有多个规则时，进行合并
     * - 比如类上指定了@RequestMapping的 url 为 root
     * - 而方法上指定的@RequestMapping的 url 为 method
     * - 那么在获取这个接口的 url 匹配规则时，类上扫描一次，方法上扫描一次，这个时候就需要把这两个合并成一个，表示这个接口匹配root/method
     *
     * @param other 相同api version condition
     * @return ApiVersionCondition
     */
    @Override
    public ApiVersionCondition combine(ApiVersionCondition other) {
        // 此处按优先级，method大于class
        return new ApiVersionCondition(other.getApiVersion(), apiPlatform, versionProperties);
    }

    /**
     * 判断是否成功，失败返回 null；否则，则返回匹配成功的条件
     *
     * @param httpServletRequest http request
     * @return 匹配成功条件
     */
    @Override
    public ApiVersionCondition getMatchingCondition(HttpServletRequest httpServletRequest) {
        // 通过uri匹配版本号
        Matcher m = VERSION_PREFIX_PATTERN.matcher(httpServletRequest.getRequestURI());
        if (m.find()) {
            // 获得符合匹配条件的ApiVersionCondition
            double version = Double.valueOf(m.group(1));
            if (version >= getApiVersion().version()) {
                return this;
            }
        }
        return null;
    }

    /**
     * 多个都满足条件时，用来指定具体选择哪一个
     *
     * @param other              多个时
     * @param httpServletRequest http request
     * @return 取版本号最大的
     */
    @Override
    public int compareTo(ApiVersionCondition other, HttpServletRequest httpServletRequest) {
        // 当出现多个符合匹配条件的ApiVersionCondition，优先匹配版本号较大的
        return other.getApiVersion().version() >= getApiVersion().version() ? 1 : -1;
    }

}