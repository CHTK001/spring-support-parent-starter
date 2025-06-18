package com.chua.starter.common.support.control;


import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.annotations.ApiVersion;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * API version condition 条件
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
    private final ApiVersion apiVersion;

    ApiVersionCondition(ApiVersion apiVersion) {
        this.apiVersion = apiVersion;
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
        return new ApiVersionCondition(other.getApiVersion());
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
        Map<String, String> stringStringMap = MapUtils.asMap(httpServletRequest.getQueryString(), "&", "=");
        String versionStr = MapUtils.getString(stringStringMap, "version");
        if (null == versionStr) {
            return this;
        }

        // 获得符合匹配条件的ApiVersionCondition
        ApiVersion currentApiVersion = getApiVersion();
        if (null == currentApiVersion) {
            return this;
        }

        String currentVersion = currentApiVersion.version();

        // 转换为双精度比较
        double requestVersion = normalizeVersion(versionStr);
        double apiVersion = normalizeVersion(currentVersion);

        // 判断版本号是否符合要求
        if (isVersionCompatible(requestVersion, apiVersion)) {
            return this;
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
        double thisVersion = normalizeVersion(getApiVersion().version());
        double otherVersion = normalizeVersion(other.getApiVersion().version());
        return Double.compare(otherVersion, thisVersion);
    }

    /**
     * 标准化版本号，将1和1.0视为相同版本
     *
     * @param version 版本号字符串
     * @return 标准化后的双精度版本号
     */
    private double normalizeVersion(String version) {
        if (version == null || version.isEmpty()) {
            return 0.0;
        }

        // 移除可能的+后缀
        if (version.endsWith("+")) {
            version = version.substring(0, version.length() - 1);
        }

        try {
            return Double.parseDouble(version);
        } catch (NumberFormatException e) {
            log.warn("无法解析版本号: {}", version);
            return 0.0;
        }
    }

    /**
     * 判断请求版本是否与API版本兼容
     * 支持以下比较方式：
     * 1. 精确匹配: 1.0 = 1.0, 1 = 1.0
     * 2. 向后兼容: 请求版本 >= API版本时匹配成功
     * 3. 带+号的版本表示向后兼容: 1.0+ >= 1.0
     *
     * @param requestVersion 请求版本号
     * @param apiVersion     API定义的版本号
     * @return 是否兼容
     */
    private boolean isVersionCompatible(double requestVersion, double apiVersion) {
        // 请求版本号大于等于API版本号，表示兼容
        return requestVersion >= apiVersion;
    }
}