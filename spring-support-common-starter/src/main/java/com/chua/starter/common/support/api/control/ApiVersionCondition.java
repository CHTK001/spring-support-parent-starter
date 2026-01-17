package com.chua.starter.common.support.api.control;
import com.chua.common.support.network.net.Version;
import com.chua.common.support.core.utils.MapUtils;
import com.chua.starter.common.support.api.annotations.ApiVersion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * API 版本条件
 * <p>
 * 用于匹配请求中的版本号与 API 定义的版本号。
 * </p>
 * <p>
 * 支持的版本指定方式：
 * <ul>
 *   <li>查询参数：?apiVersion=1 或 ?apiVersion=v1</li>
 *   <li>请求头：X-Api-Version: 1</li>
 *   <li>特殊值：apiVersion=latest 自动匹配最新版本</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @since 2020-11-16
 */
@Slf4j
public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {
        /**
     * 版本参数名
     */
    private static final String VERSION_PARAM = "apiVersion";

    /**
     * 版本请求头名
     */
    private static final String VERSION_HEADER = "X-Api-Version";

    /**
     * 最新版本标识
     */
    private static final String LATEST = "latest";

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
     * 支持的版本指定方式：
     * 1. 查询参数：?apiVersion=1 或 ?apiVersion=v1 或 ?apiVersion=latest
     * 2. 请求头：X-Api-Version: 1 或 X-Api-Version: latest
     * </p>
     *
     * @param httpServletRequest HTTP 请求
     * @return 匹配成功的条件，失败返回 null
     */
    @Override
    public ApiVersionCondition getMatchingCondition(HttpServletRequest httpServletRequest) {
        // 1. 从查询参数获取版本号 ?apiVersion=1 或 ?apiVersion=v1 或 ?apiVersion=latest
        Map<String, String> queryParams = MapUtils.asMap(httpServletRequest.getQueryString(), "&", "=");
        String versionStr = MapUtils.getString(queryParams, VERSION_PARAM);
        
        // 2. 如果没有查询参数，尝试从请求头获取 X-Api-Version
        if (null == versionStr || versionStr.isEmpty()) {
            versionStr = httpServletRequest.getHeader(VERSION_HEADER);
        }
        
        // 没有指定版本，匹配所有
        if (null == versionStr || versionStr.isEmpty()) {
            return this;
        }
        
        // 支持 latest 关键字，表示匹配最新版本
        if (LATEST.equalsIgnoreCase(versionStr)) {
            // latest 总是匹配，由 compareTo 决定优先级（版本号最大的优先）
            return this;
        }

        ApiVersion currentApiVersion = getApiVersion();
        if (null == currentApiVersion) {
            return this;
        }

        // 比较请求版本与当前API版本（语义化版本）
        Version requestVersion = parseVersion(versionStr);
Version apiVersionValue = Version.parse(currentApiVersion.value());
        
        // 请求版本 >= API版本 则匹配
        if (requestVersion.compareTo(apiVersionValue) >= 0) {
            return this;
        }

        return null;
    }

    /**
     * 判断请求是否指定了 latest 版本
     *
     * @param httpServletRequest HTTP 请求
     * @return 是否为 latest
     */
    private boolean isLatestVersion(HttpServletRequest httpServletRequest) {
        Map<String, String> queryParams = MapUtils.asMap(httpServletRequest.getQueryString(), "&", "=");
        String versionStr = MapUtils.getString(queryParams, VERSION_PARAM);
        if (null == versionStr || versionStr.isEmpty()) {
            versionStr = httpServletRequest.getHeader(VERSION_HEADER);
        }
        return LATEST.equalsIgnoreCase(versionStr);
    }
    
    /**
     * 解析版本号字符串
     *
     * @param versionStr 版本号字符串
     * @return 版本号数值
     */
    private Version parseVersion(String versionStr) {
        try {
            return Version.parse(versionStr);
        } catch (Exception e) {
            log.warn("无法解析版本号: {}", versionStr);
            return Version.parse("1.0.0");
        }
    }

    /**
     * 比较优先级（版本号大的优先）
     * <p>
     * 当请求指定 apiVersion=latest 时，版本号最大的接口优先匹配。
     * </p>
     *
     * @param other              另一个条件
     * @param httpServletRequest HTTP 请求
     * @return 比较结果
     */
    @Override
    public int compareTo(ApiVersionCondition other, HttpServletRequest httpServletRequest) {
        Version thisVersion = getApiVersion() != null ? Version.parse(getApiVersion().value()) : Version.parse("0");
        Version otherVersion = other.getApiVersion() != null ? Version.parse(other.getApiVersion().value()) : Version.parse("0");
        
        // 如果是 latest，请求需要选择最大可用版本（降序）
        if (isLatestVersion(httpServletRequest)) {
            return otherVersion.compareTo(thisVersion);
        }
        
        // 普通情况：选择不大于请求版本中的最大版本，依旧按照版本降序
        return otherVersion.compareTo(thisVersion);
    }
    /**
     * 获取 apiVersion
     *
     * @return apiVersion
     */
    public ApiVersion getApiVersion() {
        return apiVersion;
    }


}

