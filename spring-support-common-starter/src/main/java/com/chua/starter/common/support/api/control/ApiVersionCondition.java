package com.chua.starter.common.support.api.control;
import com.chua.common.support.lang.version.Version;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.api.annotations.ApiVersion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
* @since 2020-11-16
* @author CH
 */
public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(ApiVersionCondition.class);
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
    * API 版本 接口
    */
    private final ApiVersion apiVersion;

    /**
    * 创建 api版本条件 实例
    * @param apiVersion api版本
    */
    public ApiVersionCondition(ApiVersion apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
    * 合并条件（方法 优先于 类）
    *
    * @param other 另一个条件
    * @return 合并后的条件
    */
    @Override
    public ApiVersionCondition combine(ApiVersionCondition other) {
        /**
        * api版本条件。
        * @param other.getApiVersion( other.获取api版本(
        */
        return new ApiVersionCondition(other.getApiVersion());
    }

    /**
    * 判断是否匹配
    * <p>
    * 支持的版本指定方式：
    * 1. 查询参数：?api版本=1 或 ?api版本=v1 或 ?api版本=latest
    * 2. 请求头：X-Api-版本: 1 或 X-Api-版本: latest
    * </p>
    *
    * @param httpServletRequest HTTP 请求
    * @return 匹配成功的条件，失败返回 空
    */
    @Override
    public ApiVersionCondition getMatchingCondition(HttpServletRequest httpServletRequest) {
 // 1. 从查询参数获取版本号 ?api版本=1 或 ?api版本=v1 或 ?api版本=latest
 // 使用 获取参数 而非解析 获取查询字符串：查询字符串 在部分环境（mockmvc）为 空，
        //    且手工切分存在编码/嵌套解析问题
        String versionStr = httpServletRequest.getParameter(VERSION_PARAM);

 // 2. 如果没有查询参数，尝试从请求头获取 X-Api-版本
        if (null == versionStr || versionStr.isEmpty()) {
            versionStr = httpServletRequest.getHeader(VERSION_HEADER);
        }
        
        // 没有指定版本，匹配所有
        if (null == versionStr || versionStr.isEmpty()) {
            return this;
        }
        
        // 支持 latest 关键字，表示匹配最新版本
        if (LATEST.equalsIgnoreCase(versionStr)) {
 // latest 总是匹配，由 compare转为 决定优先级（版本号最大的优先）
            return this;
        }

        ApiVersion currentApiVersion = getApiVersion();
        if (null == currentApiVersion) {
            return this;
        }

        // 比较请求版本与当前API版本（语义化版本）
        Version requestVersion = parseVersion(versionStr);
Version apiVersionValue = Version.of(currentApiVersion.value());
        
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
        String versionStr = httpServletRequest.getParameter(VERSION_PARAM);
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
            return Version.of(versionStr);
        } catch (Exception e) {
            log.warn("无法解析版本号: {}", versionStr);
            return Version.of("1.0.0");
        }
    }

    /**
    * 比较优先级（版本号大的优先）
    * <p>
    * 当请求指定 api版本=latest 时，版本号最大的接口优先匹配。
    * </p>
    *
    * @param other              另一个条件
    * @param httpServletRequest HTTP 请求
    * @return 比较结果
    */
    @Override
    public int compareTo(ApiVersionCondition other, HttpServletRequest httpServletRequest) {
        Version thisVersion = getApiVersion() != null ? Version.of(getApiVersion().value()) : Version.of("0");
        Version otherVersion = other.getApiVersion() != null ? Version.of(other.getApiVersion().value()) : Version.of("0");
        
        // 如果是 latest，请求需要选择最大可用版本（降序）
        if (isLatestVersion(httpServletRequest)) {
            return otherVersion.compareTo(thisVersion);
        }
        
        // 普通情况：选择不大于请求版本中的最大版本，依旧按照版本降序
        return otherVersion.compareTo(thisVersion);
    }
    /**
    * 获取 api版本
    *
    * @return apiVersion
    */
    public ApiVersion getApiVersion() {
        return apiVersion;
    }


}
