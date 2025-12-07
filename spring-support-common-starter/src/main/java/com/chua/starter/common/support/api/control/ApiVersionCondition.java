package com.chua.starter.common.support.api.control;

import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.api.annotations.ApiVersion;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * API 版本条件
 * <p>
 * 用于匹配请求中的版本号与 API 定义的版本号。
 * </p>
 *
 * @author CH
 * @since 2020-11-16
 */
@Getter
@Slf4j
public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {

    /**
     * 接口路径中的版本号前缀，如: api/v[1-n]/test
     */
    private final static Pattern VERSION_PREFIX_PATTERN = Pattern.compile("/v([0-9]+\\.{0,1}[0-9]{0,2})/");

    /**
     * API VERSION interface
     */
    private final ApiVersion apiVersion;

    public ApiVersionCondition(ApiVersion apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * 合并条件（method 优先于 class）
     *
     * @param other 另一个条件
     * @return 合并后的条件
     */
    @Override
    public ApiVersionCondition combine(ApiVersionCondition other) {
        return new ApiVersionCondition(other.getApiVersion());
    }

    /**
     * 判断是否匹配
     * <p>
     * 支持两种版本指定方式：
     * 1. 查询参数：?version=1 或 ?version=v1
     * 2. 路径前缀：/api/v1/xxx
     * </p>
     *
     * @param httpServletRequest HTTP 请求
     * @return 匹配成功的条件，失败返回 null
     */
    @Override
    public ApiVersionCondition getMatchingCondition(HttpServletRequest httpServletRequest) {
        // 1. 从查询参数获取版本号 ?version=1 或 ?version=v1
        Map<String, String> queryParams = MapUtils.asMap(httpServletRequest.getQueryString(), "&", "=");
        String versionStr = MapUtils.getString(queryParams, "version");
        
        // 2. 如果没有查询参数，尝试从路径中获取版本号 /api/v1/xxx
        if (null == versionStr) {
            java.util.regex.Matcher matcher = VERSION_PREFIX_PATTERN.matcher(httpServletRequest.getRequestURI());
            if (matcher.find()) {
                versionStr = matcher.group(1);
            }
        }
        
        // 没有指定版本，匹配所有
        if (null == versionStr) {
            return this;
        }
        
        // 移除版本号前缀 v/V
        if (versionStr.startsWith("v") || versionStr.startsWith("V")) {
            versionStr = versionStr.substring(1);
        }

        ApiVersion currentApiVersion = getApiVersion();
        if (null == currentApiVersion) {
            return this;
        }

        // 比较请求版本与当前API版本
        double requestVersion = parseVersion(versionStr);
        double apiVersionValue = currentApiVersion.value();
        
        // 请求版本 >= API版本 则匹配
        if (requestVersion >= apiVersionValue) {
            return this;
        }

        return null;
    }
    
    /**
     * 解析版本号字符串
     *
     * @param versionStr 版本号字符串
     * @return 版本号数值
     */
    private double parseVersion(String versionStr) {
        try {
            return Double.parseDouble(versionStr);
        } catch (NumberFormatException e) {
            log.warn("无法解析版本号:  {}", versionStr);
            return 0.0;
        }
    }

    /**
     * 比较优先级（版本号大的优先）
     *
     * @param other              另一个条件
     * @param httpServletRequest HTTP 请求
     * @return 比较结果
     */
    @Override
    public int compareTo(ApiVersionCondition other, HttpServletRequest httpServletRequest) {
        double thisVersion = getApiVersion() != null ? getApiVersion().value() : 0.0;
        double otherVersion = other.getApiVersion() != null ? other.getApiVersion().value() : 0.0;
        // 版本号大的优先匹配
        return Double.compare(otherVersion, thisVersion);
    }
}

