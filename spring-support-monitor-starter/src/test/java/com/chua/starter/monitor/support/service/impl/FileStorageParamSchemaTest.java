package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.server.filter.ServerFilterFactory;
import com.chua.starter.monitor.support.server.model.ServerPluginParamVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务管理 —— file-storage 插件参数面的一致性门禁。
 *
 * <p>背景：参数从「声明」到「生效」要穿过两道彼此独立、却必须完全对齐的清单：</p>
 * <ol>
 *   <li><b>schema</b>：{@code ServerManageServiceImpl#fileStorageParams()}，
 *       下发给前端渲染表单；</li>
 *   <li><b>白名单</b>：{@code ServerFilterFactory} 的 {@code SETTING_BOOLEAN_KEYS} /
 *       {@code SETTING_STRING_KEYS}，决定参数能否被 {@code applySettingParams} 落到
 *       {@code FileStorageSetting}。</li>
 * </ol>
 *
 * <p>两者曾长期不一致，且两个方向都真实发生过：</p>
 * <ul>
 *   <li><b>声明了但不生效</b>：{@code showFormat/showSize/showTime/imageFilter} 在 schema 里
 *       可配，却不在白名单里，{@code applySettingParams} 只记一条 WARN 后丢弃——用户能改、
 *       界面有控件、服务端当没看见；</li>
 *   <li><b>生效但没声明</b>：{@code layout}（目录列表页总开关，false 时访问目录直接 404）
 *       在白名单里却没进 schema，前端 schema 驱动表单渲染不出它，只能改数据库，
 *       于是目录列表页恒为 404 且无任何界面入口。</li>
 * </ul>
 *
 * <p>这类漂移编译期无感知、运行期也只是 WARN 或 404，只能靠本门禁拦住。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileStorageParamSchemaTest {

    /**
     * 读取 {@code ServerFilterFactory} 的参数白名单。
     *
     * @param fieldName 白名单字段名
     * @return 参数名集合
     * @throws Exception 反射失败
     */
    private static Set<String> whitelist(String fieldName) throws Exception {
        Field field = ServerFilterFactory.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> value = (List<String>) field.get(null);
        return new LinkedHashSet<>(value);
    }

    /**
     * 读取服务管理下发的 file-storage 参数名。
     *
     * @return 参数名集合，保持声明顺序
     */
    private static Set<String> declaredSchemaNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ServerPluginParamVO param : ServerManageServiceImpl.fileStorageParams()) {
            names.add(param.name());
        }
        return names;
    }

    @Test
    @DisplayName("schema 声明的参数集合 == 白名单接受的参数集合（双向都不允许有差集）")
    void schemaAndWhitelistMustMatch() throws Exception {
        Set<String> declared = declaredSchemaNames();
        Set<String> accepted = new LinkedHashSet<>(whitelist("SETTING_BOOLEAN_KEYS"));
        accepted.addAll(whitelist("SETTING_STRING_KEYS"));

        Set<String> declaredOnly = new LinkedHashSet<>(declared);
        declaredOnly.removeAll(accepted);
        Set<String> acceptedOnly = new LinkedHashSet<>(accepted);
        acceptedOnly.removeAll(declared);

        assertTrue(declaredOnly.isEmpty(),
                "这些参数声明给了前端却没有落位白名单，改了不生效: " + declaredOnly);
        assertTrue(acceptedOnly.isEmpty(),
                "这些参数能落位却没有声明，前端渲染不出、用户无从配置: " + acceptedOnly);
        assertEquals(declared.size(), accepted.size(),
                "schema 与白名单的参数量应一致");
    }

    @Test
    @DisplayName("目录列表页总开关 layout 必须既在 schema 又在白名单")
    void layoutMustBeBothDeclaredAndAccepted() throws Exception {
        assertTrue(declaredSchemaNames().contains("layout"),
                "layout 是目录列表页总开关，漏声明会让 schema 驱动表单渲染不出它，目录页恒 404");
        assertTrue(whitelist("SETTING_BOOLEAN_KEYS").contains("layout"),
                "layout 缺在布尔白名单里，applySettingParams 只会丢弃");
    }

    @Test
    @DisplayName("曾被静默丢弃的 4 个参数必须已在白名单内")
    void previouslyDroppedParamsAreNowAccepted() throws Exception {
        Set<String> accepted = new LinkedHashSet<>(whitelist("SETTING_BOOLEAN_KEYS"));
        accepted.addAll(whitelist("SETTING_STRING_KEYS"));
        for (String name : List.of("showFormat", "showSize", "showTime", "imageFilter")) {
            assertTrue(accepted.contains(name),
                    name + " 曾声明可配却不在白名单内，只记 WARN 后丢弃");
        }
    }

    @Test
    @DisplayName("枚举参数 layoutType 的候选值与 FileStorageSetting.LayoutType 一致")
    void layoutTypeOptionsMatchEnum() {
        Set<String> options = null;
        for (ServerPluginParamVO param : ServerManageServiceImpl.fileStorageParams()) {
            if ("layoutType".equals(param.name())) {
                options = new LinkedHashSet<>(param.options());
            }
        }
        assertTrue(options != null && !options.isEmpty(), "layoutType 应以 enum 形式声明并带候选值");
        assertEquals(Set.of("SIMPLE", "SYSTEM"), options,
                "layoutType 候选值必须与 FileStorageSetting.LayoutType 枚举常量一一对应");
    }
}
