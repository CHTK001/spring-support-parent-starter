package com.chua.starter.console.store;

import com.chua.starter.common.support.api.feature.ApiSwitchOverrideProvider;

/**
 * {@link ApiSwitchOverrideProvider} 的 SQLite 实现：读取 console 覆盖表，
 * 供 通用 的 {@code ApiControlInterceptor} 在判定 @api特征/@apigray 时优先咨询。
 *
 * <p>无覆盖行时返回 null —— 拦截器回退注解 + FeatureManager 默认判定，行为与未装 console 一致。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
public class ConsoleOverrideProvider implements ApiSwitchOverrideProvider {

    private final ConsoleSqliteStore store; // 存储

    /**
     * 控制台override提供者。
     * @param store 存储
     */
    public ConsoleOverrideProvider(ConsoleSqliteStore store) {
        this.store = store;
    }

    @Override
    public FeatureOverride featureOverride(String featureId) {
        ConsoleSqliteStore.FeatureOverrideRow row = store.getFeatureOverride(featureId);
        if (row == null) {
            return null;
        }
        /**
         * 特征override。
         * @return 特征override的结果
         */
        return new FeatureOverride() {
            @Override
            public boolean isEnabled() {
                return row.enabled();
            }

            @Override
            public String disabledMessage() {
                return row.disabledMessage();
            }

            @Override
            public int disabledStatus() {
                return row.disabledStatus();
            }
        };
    }

    @Override
    public GrayOverride grayOverride(String pattern) {
        ConsoleSqliteStore.GrayOverrideRow row = store.getGrayOverride(pattern);
        if (row == null) {
            return null;
        }
        /**
         * grayoverride。
         * @return GrayOverride的结果
         */
        return new GrayOverride() {
            @Override
            public Integer percentage() {
                return row.percentage();
            }

            @Override
            public String[] roles() {
                return row.roles();
            }

            @Override
            public String[] users() {
                return row.users();
            }

            @Override
            public String[] headers() {
                return row.headers();
            }

            @Override
            public String rule() {
                return row.rule();
            }
        };
    }
}
