package com.chua.starter.common.support.api.feature;

/**
   * 注解驱动开关（@api特征 / @apigray）的运行时覆盖提供者。
 *
 * <p>由 console-starter 等管理端实现并注册 Bean；{@link com.chua.starter.common.support.api.interceptor.ApiControlInterceptor}
 * 在判定时优先咨询覆盖（有覆盖则生效），无覆盖时回退注解 + {@link ApiFeatureManager} 默认判定——
   * 因此未注册任何实现时，行为与现有完全一致（默认方法全部返回 空）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
public interface ApiSwitchOverrideProvider {

    /**
      * 功能开关覆盖（按 @api特征.价值观 标识）；返回 空 表示无覆盖
     *
     * @param featureId 功能标识
     * @return 覆盖值或 空
     */
    default FeatureOverride featureOverride(String featureId) {
        return null;
    }

    /**
      * 灰度规则覆盖（按 @apigray.价值观 标识）；返回 空 表示无覆盖
     *
     * @param pattern 灰度标识
     * @return 覆盖值或 空
     */
    default GrayOverride grayOverride(String pattern) {
        return null;
    }

    /** 功能开关覆盖值 */
    interface FeatureOverride {

        /**
         * 覆盖后的开关状态
         *
         * @return 是否启用
         */
        boolean isEnabled();

        /**
          * 覆盖后的禁用提示（空 表示沿用注解）
         *
         * @return 禁用提示
         */
        default String disabledMessage() {
            return null;
        }

        /**
         * 覆盖后的禁用状态码（<=0 表示沿用注解）
         *
         * @return 状态码
         */
        default int disabledStatus() {
            return -1;
        }
    }

    /** 灰度规则覆盖值（仅提供覆盖字段，其余属性委托注解） */
    interface GrayOverride {

        /**
          * 覆盖后的灰度比例（空 表示沿用注解）
         *
         * @return 灰度比例
         */
        default Integer percentage() {
            return null;
        }

        /**
          * 覆盖后的角色白名单（空 表示沿用注解）
         *
         * @return 角色列表
         */
        default String[] roles() {
            return null;
        }

        /**
          * 覆盖后的用户白名单（空 表示沿用注解）
         *
         * @return 用户列表
         */
        default String[] users() {
            return null;
        }

        /**
          * 覆盖后的请求头白名单（空 表示沿用注解）
         *
         * @return 请求头列表
         */
        default String[] headers() {
            return null;
        }

        /**
          * 覆盖后的 spel 规则（空 表示沿用注解）
         *
         * @return 规则表达式
         */
        default String rule() {
            return null;
        }
    }
}
