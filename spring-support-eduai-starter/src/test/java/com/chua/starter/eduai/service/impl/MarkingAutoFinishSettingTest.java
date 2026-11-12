// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.service.impl;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.paper.pipeline.config.EduAiProperties;
import com.chua.starter.ai.support.paper.pipeline.setting.MarkingSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「判分完成即自动结案」开关的读取口径回归测试。
 *
 * <p>这条开关决定判分结束后是否替老师把任务收口（登记错题 + 置「已完成」）。
 * 读错一个值就会改变所有学校的默认行为，因此必须由单测守住三条边界：</p>
 * <ul>
 *   <li>未配置 / 值非法 → <b>关闭</b>，行为与改造前完全一致；</li>
 *   <li>配置 {@code true}（或 {@code 1} / {@code yes}）→ 开启；</li>
 *   <li>配置 {@code false} → 明确关闭，不得被空值兜底成开启。</li>
 * </ul>
 *
 * <p>自动结案在后台线程执行，没有用户上下文；「未识别学生」是它唯一预期会失败的
 * 业务分支，此时只跳过结案、不改判分状态，因此一并守住
 * {@link EduMarkingServiceImpl#isUnidentifiedStudent(String)} 的判定口径。</p>
 *
 * <p>本测试不启动 Spring 上下文、不引入 mock 框架：被测对象是纯配置解析与纯判定函数。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class MarkingAutoFinishSettingTest {

    /**
     * 全局设置分组名（与 {@code MarkingGlobalSettingProvider} 一致）
     */
    private static final String GROUP = "marking";

    /**
     * 开关字段名（与配置提供者一致，写错就会「页面保存了却不生效」）
     */
    private static final String FIELD = "auto_finish";

    /**
     * 未配置任何值时默认关闭。
     */
    @Test
    @DisplayName("自动结案：未配置时默认关闭")
    void autoFinish_defaultOff() {
        MarkingSettingService service = new MarkingSettingService(new EduAiProperties());
        assertFalse(service.autoFinish(), "未配置 auto_finish 时必须保持原行为（停在已判分）");
    }

    /**
     * 配置 true 时开启。
     */
    @Test
    @DisplayName("自动结案：配置 true 时开启")
    void autoFinish_trueEnables() throws Exception {
        MarkingSettingService service = serviceWith(Map.of(FIELD, "true"));
        assertTrue(service.autoFinish());
    }

    /**
     * 布尔字段兼容 1 / yes（与 {@code readBool} 的既有口径一致）。
     */
    @Test
    @DisplayName("自动结案：1 / yes 同样视为开启")
    void autoFinish_numericAndYesEnable() throws Exception {
        assertTrue(serviceWith(Map.of(FIELD, "1")).autoFinish());
        assertTrue(serviceWith(Map.of(FIELD, "YES")).autoFinish());
    }

    /**
     * 配置 false 时明确关闭。
     */
    @Test
    @DisplayName("自动结案：配置 false 时关闭")
    void autoFinish_falseDisables() throws Exception {
        assertFalse(serviceWith(Map.of(FIELD, "false")).autoFinish());
    }

    /**
     * 值非法（页面被写坏）时回落关闭，不能误开。
     */
    @Test
    @DisplayName("自动结案：非法值回落为关闭")
    void autoFinish_garbageFallsBackToOff() throws Exception {
        assertFalse(serviceWith(Map.of(FIELD, "maybe")).autoFinish());
    }

    /**
     * 空字符串等同未配置。
     */
    @Test
    @DisplayName("自动结案：空字符串等同未配置")
    void autoFinish_blankFallsBackToOff() throws Exception {
        assertFalse(serviceWith(Map.of(FIELD, "   ")).autoFinish());
    }

    /**
     * 未识别学生：空 / 空白 / 占位 anonymous 都要挡住，否则会生成无主错题。
     */
    @Test
    @DisplayName("未识别学生：空值与 anonymous 占位都算未识别")
    void unidentifiedStudent_gate() {
        assertTrue(EduMarkingServiceImpl.isUnidentifiedStudent(null));
        assertTrue(EduMarkingServiceImpl.isUnidentifiedStudent(""));
        assertTrue(EduMarkingServiceImpl.isUnidentifiedStudent("   "));
        assertTrue(EduMarkingServiceImpl.isUnidentifiedStudent("anonymous"));
        assertTrue(EduMarkingServiceImpl.isUnidentifiedStudent("  Anonymous "));
        assertFalse(EduMarkingServiceImpl.isUnidentifiedStudent("20260301"));
    }

    /**
     * 构造一个注入「只读全局层」假读取器的设置服务。
     *
     * @param values 字段名 → 值
     * @return 设置服务
     * @throws Exception 反射写入失败
     */
    private static MarkingSettingService serviceWith(Map<String, String> values) throws Exception {
        MarkingSettingService service = new MarkingSettingService(new EduAiProperties());
        Field field = MarkingSettingService.class.getDeclaredField("globalSettingReader");
        field.setAccessible(true);
        field.set(service, new MapGlobalSettingReader(values));
        return service;
    }

    /**
     * 只实现 {@code getStrDirect} 的最小读取器：其余方法不会被设置服务调用。
     */
    private static final class MapGlobalSettingReader implements GlobalSettingReader {

        /**
         * 字段名 → 值
         */
        private final Map<String, String> values = new HashMap<>();

        /**
         * @param source 字段名 → 值（均属于 marking 分组）
         */
        private MapGlobalSettingReader(Map<String, String> source) {
            this.values.putAll(source);
        }

        @Override
        public String getStr(String group, String field) {
            return getStrDirect(group, field);
        }

        @Override
        public String getStrAs(String userId, String group, String field) {
            return getStrDirect(group, field);
        }

        @Override
        public String getStrDirect(String group, String field) {
            return GROUP.equals(group) ? values.get(field) : null;
        }

        @Override
        public boolean getBool(String group, String field, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public int getInt(String group, String field, int defaultValue) {
            return defaultValue;
        }

        @Override
        public Integer getIntOrNull(String group, String field) {
            return null;
        }

        @Override
        public Long getLongOrNull(String group, String field) {
            return null;
        }

        @Override
        public BigDecimal getDecimalOrNull(String group, String field) {
            return null;
        }

        @Override
        public double getDouble(String group, String field, double defaultValue) {
            return defaultValue;
        }
    }
}
