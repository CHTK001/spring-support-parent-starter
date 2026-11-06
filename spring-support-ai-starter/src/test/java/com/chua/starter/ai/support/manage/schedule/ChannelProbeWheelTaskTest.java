// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.schedule;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.health.ChannelProbeGlobalSettingProvider;
import com.chua.starter.ai.support.service.ChannelHealthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ChannelProbeWheelTask} 的间隔解析测试。
 *
 * <p>只覆盖「配置 → 生效间隔」这一段纯逻辑：渠道级配置优先于全局、
 * 缺失/0 回落全局、结果钳到 30 秒下限。时间轮与探针本体是既有组件，
 * 不在本用例范围内（探针行为由 {@link ChannelHealthService} 的用例覆盖）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
class ChannelProbeWheelTaskTest {

    /**
     * 内存版设置读取器：只实现被测路径需要的 {@code getInt}/{@code getBool}
     */
    private static final class MapSettingReader implements GlobalSettingReader {

        /**
         * 键 = {@code group + "." + field}
         */
        private final Map<String, String> values = new HashMap<>();

        /**
         * 写入配置。
         *
         * @param group 分组
         * @param field 字段
         * @param value 值
         * @return 自身
         */
        MapSettingReader put(String group, String field, String value) {
            values.put(group + "." + field, value);
            return this;
        }

        @Override
        public String getStr(String group, String field) {
            return values.get(group + "." + field);
        }

        @Override
        public String getStrAs(String userId, String group, String field) {
            return getStr(group, field);
        }

        @Override
        public String getStrDirect(String group, String field) {
            return getStr(group, field);
        }

        @Override
        public boolean getBool(String group, String field, boolean defaultValue) {
            String value = getStr(group, field);
            return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
        }

        @Override
        public int getInt(String group, String field, int defaultValue) {
            String value = getStr(group, field);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }

        @Override
        public Integer getIntOrNull(String group, String field) {
            String value = getStr(group, field);
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        }

        @Override
        public Long getLongOrNull(String group, String field) {
            String value = getStr(group, field);
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        }

        @Override
        public BigDecimal getDecimalOrNull(String group, String field) {
            String value = getStr(group, field);
            return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
        }

        @Override
        public double getDouble(String group, String field, double defaultValue) {
            String value = getStr(group, field);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
    }

    /**
     * 构造被测任务（探针服务在此用例中不会被调用）。
     *
     * @param reader 设置读取器
     * @return 任务
     */
    private static ChannelProbeWheelTask task(GlobalSettingReader reader) {
        return new ChannelProbeWheelTask(reader, null, null);
    }

    @Test
    @DisplayName("intervalSec：渠道级配置优先于全局")
    void perChannelOverridesGlobal() {
        MapSettingReader reader = new MapSettingReader()
                .put("health", "channel_health_probe_interval_sec", "60")
                .put("channel_probe", ChannelProbeGlobalSettingProvider.FIELD_PREFIX + "workbuddy", "300");
        assertEquals(300L, task(reader).intervalSec("workbuddy"));
    }

    @Test
    @DisplayName("intervalSec：渠道级为 0 或缺失时回落全局")
    void fallsBackToGlobal() {
        MapSettingReader reader = new MapSettingReader()
                .put("health", "channel_health_probe_interval_sec", "120")
                .put("channel_probe", ChannelProbeGlobalSettingProvider.FIELD_PREFIX + "workbuddy", "0");
        assertEquals(120L, task(reader).intervalSec("workbuddy"));
        assertEquals(120L, task(reader).intervalSec("cline"));
    }

    @Test
    @DisplayName("intervalSec：低于 30 秒的配置被钳到下限（避免打爆上游）")
    void clampsToMinimum() {
        MapSettingReader reader = new MapSettingReader()
                .put("channel_probe", ChannelProbeGlobalSettingProvider.FIELD_PREFIX + "workbuddy", "5");
        assertEquals(30L, task(reader).intervalSec("workbuddy"), "1s 探针会把上游限流打满，必须钳到 30s");

        MapSettingReader global = new MapSettingReader()
                .put("health", "channel_health_probe_interval_sec", "10");
        assertEquals(30L, task(global).intervalSec("workbuddy"), "全局值同样受下限约束");
    }

    @Test
    @DisplayName("intervalSec：全局也缺失时用 60 秒兜底")
    void defaultInterval() {
        assertEquals(60L, task(new MapSettingReader()).intervalSec("workbuddy"));
    }
}
