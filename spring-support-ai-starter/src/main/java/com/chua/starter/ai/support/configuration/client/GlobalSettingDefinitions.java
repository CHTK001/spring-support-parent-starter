package com.chua.starter.ai.support.configuration.client;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局设置「分组定义」的缓存持有者。
 *
 * <p><b>为什么需要缓存。</b>分组定义由 SPI 发现得到，而同一句
 * {@code ServiceProvider.of(GlobalSettingProvider.class).collect()} 在不同线程下结果不同：
 * 启动期 {@link GlobalSettingInitializer} 在 main 线程跑，能发现全部分组
 * （启动日志有「分组 'generate' … 已有 N 项」为证）；而控制器在 tomcat 请求线程里跑，
 * {@code collect()} 返回空列表。于是 {@code GET /v2/global-setting-definitions} 回
 * {@code data: []}，前端设置页的 {@code v-for} 无内容也没有空状态，整块空白。
 * 根因在框架的 SPI 发现（类加载器 / 解析器选择），属于公共层，不适合在业务侧顺手改。</p>
 *
 * <p><b>这里怎么绕开。</b>发现结果在启动期已经成功过一次，把它缓存下来供请求线程读即可：
 * 请求线程不再需要自己跑一遍扫描。启动预热由 {@link GlobalSettingInitializer} 触发。</p>
 *
 * <p><b>空结果不写缓存。</b>否则一次失败的扫描会被永久钉住，之后所有请求都拿到空 ——
 * 那正是现在这个问题的形态，不能把它固化下来。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class GlobalSettingDefinitions {

    /**
     * 缓存的分组定义；仅在扫描成功且非空时写入
     */
    private volatile List<Map<String, Object>> cache;

    /**
     * 取分组定义，首次成功扫描后缓存。
     *
     * @return 分组定义列表；扫描不到任何实现时返回空列表（与调用方原有语义一致）
     */
    public List<Map<String, Object>> definitions() {
        List<Map<String, Object>> hit = cache;
        if (hit != null) {
            return hit;
        }
        List<Map<String, Object>> built = build(ServiceProvider.of(GlobalSettingProvider.class).collect());
        if (!built.isEmpty()) {
            cache = built;
            log.info("[GlobalSettingDefinitions] 缓存 {} 个分组定义", built.size());
        }
        return built;
    }

    /**
     * 启动期预热。
     *
     * <p>在 {@code main} 线程调用：那一线程上的 SPI 发现是好的（启动日志可证），
     * 把结果缓存下来后，请求线程就不必再各自扫描一遍。</p>
     */
    public void warmUp() {
        List<Map<String, Object>> built = definitions();
        if (built.isEmpty()) {
            log.warn("[GlobalSettingDefinitions] 启动预热未发现任何分组定义；"
                    + "设置页将为空，请检查 SPI 发现的类加载器配置");
        }
    }

    /**
     * 由 SPI 实现列表构造分组定义。
     *
     * <p>与控制器原先的实现逐字一致（按 group 去重、组内按 field 去重），
     * 只是搬到这里以便两处共用，避免两条路径慢慢分叉。</p>
     *
     * @param providers SPI 实现列表
     * @return 分组定义列表
     */
    private static List<Map<String, Object>> build(List<GlobalSettingProvider> providers) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Map<String, Object>> byGroup = new LinkedHashMap<>();
        for (GlobalSettingProvider gsp : providers) {
            Map<String, Object> groupMap = byGroup.get(gsp.group());
            if (groupMap == null) {
                groupMap = new LinkedHashMap<>();
                groupMap.put("group", gsp.group());
                groupMap.put("groupName", gsp.groupName());
                groupMap.put("items", new ArrayList<Map<String, Object>>());
                byGroup.put(gsp.group(), groupMap);
                result.add(groupMap);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) groupMap.get("items");
            for (GlobalSettingProvider.SettingItem item : gsp.getItems()) {
                boolean exists = items.stream().anyMatch(m -> item.field().equals(m.get("field")));
                if (exists) {
                    continue;
                }
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("field", item.field());
                itemMap.put("defaultValue", item.defaultValue());
                itemMap.put("valueType", item.valueType());
                itemMap.put("description", item.description());
                itemMap.put("sort", item.sort());
                items.add(itemMap);
            }
        }
        return result;
    }
}
