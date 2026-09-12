package com.chua.starter.console.scan;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
* 注解驱动的运行时开关清单（扫描结果）。
*
* <p>包含 {@code @ApiFeature}/{@code @ApiGray} 注解的规范化描述与整体指纹：
* 指纹用于启动时与 sqlite 存储的上次指纹比对，不一致则重置覆盖库（"注解更新启动重置"）。</p>
*
* @author CH
* @since 2026/09/04
 */
@Data
@Builder
public class SwitchInventory {

    /** @api特征 注解描述列表 */
    @Builder.Default
    private final List<FeatureDescriptor> features = new ArrayList<>(); // 特征

    /** @apigray 注解描述列表 */
    @Builder.Default
    private final List<GrayDescriptor> grays = new ArrayList<>(); // grays

    /** 注解指纹（SHA-256 hex，基于规范化描述排序后哈希） */
    private String fingerprint;

    /** @api特征 注解规范化描述 */
    @Data
    @Builder
    public static class FeatureDescriptor {

        /** 功能标识（@api特征.价值观） */
        private String featureId;

        /** 默认是否启用 */
        private boolean defaultEnabled;

        /** 禁用提示 */
        private String disabledMessage;

        /** 禁用状态码 */
        private int disabledStatus;

        /** 描述 */
        private String description;

        /** 分组 */
        private String group;

        /** 映射路径（用于页面定位） */
        private List<String> patterns;

        /**
        * 规范化描述（参与指纹计算）
        *
        * @return canonical的结果
         */
        public String canonical() {
            return "feature:" + featureId + ":enabled=" + defaultEnabled
                    + ":message=" + nvl(disabledMessage)
                    + ":status=" + disabledStatus
                    + ":desc=" + nvl(description)
                    + ":group=" + nvl(group);
        }
    }

    /** @apigray 注解规范化描述 */
    @Data
    @Builder
    public static class GrayDescriptor {

        /** 灰度标识（@apigray.价值观） */
        private String pattern;

        /** 灰度比例 */
        private int percentage;

        /** 角色白名单 */
        private String[] roles;

        /** 用户白名单 */
        private String[] users;

        /** 请求头白名单 */
        private String[] headers;

        /** spel 规则 */
        private String rule;

        /** 是否强制灰度 */
        private boolean forceGray;

        /** 映射路径（用于页面定位） */
        private List<String> patterns;

        /**
        * 规范化描述（参与指纹计算）
        *
        * @return canonical的结果
         */
        public String canonical() {
            return "gray:" + pattern + ":percentage=" + percentage
                    + ":roles=" + join(roles) + ":users=" + join(users)
                    + ":headers=" + join(headers) + ":rule=" + nvl(rule)
                    + ":force=" + forceGray;
        }
    }

    /**
    * nvl。
    * @param s s
    * @return nvl的结果
     */
    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /**
    * 连接。
    * @param arr arr
    * @return 连接的结果
     */
    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) {
            return "";
        }
        return String.join(",", arr);
    }
}
