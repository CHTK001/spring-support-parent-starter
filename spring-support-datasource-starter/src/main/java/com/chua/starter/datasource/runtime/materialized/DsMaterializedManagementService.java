// [P3C A20 豁免] A20/A30/A31: 日志/描述字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.materialized;

import com.chua.starter.datasource.annotation.MaterializedRefresh;
import com.chua.starter.datasource.annotation.MaterializedRoute;
import com.chua.starter.datasource.properties.MaterializedRouteProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 物理化运行期管理服务（只读暴露）。
 *
 * <p>只做能力盘点，不伪装能力：</p>
 * <ul>
 *   <li>配置快照：直接从 Environment 绑定 {@link MaterializedRouteProperties}，宿主未启用时如实返回 enabled=false；</li>
 *   <li>注解声明点：扫描容器中类/方法上的 {@link MaterializedRoute} / {@link MaterializedRefresh}；</li>
 *   <li>物化缓存数据源：按 cacheDataSourcePrefix 前缀在已注册数据源中命中（未命中前缀的路由会回落源库）。</li>
 * </ul>
 *
 * <p>当前物理化实现为安全降级骨架（写后刷新 no-op），不具备主动重建副本的能力，
 * 刷新接口如实返回「不支持」，由业务代码中 {@code @MaterializedRefresh} 标注的方法在调用时触发。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class DsMaterializedManagementService {

    private final ApplicationContext applicationContext;
    private final Environment environment;

    public DsMaterializedManagementService(ApplicationContext applicationContext, Environment environment) {
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    /**
     * 物理化配置快照。
     *
     * @return 配置键值（enabled / defaultThreshold / refreshIntervalSeconds / skipQueryInTransaction / cacheDataSourcePrefix）
     */
    public Map<String, Object> config() {
        MaterializedRouteProperties properties = MaterializedRouteProperties.bind(environment);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("defaultThreshold", properties.getDefaultThreshold());
        result.put("refreshIntervalSeconds", properties.getRefreshIntervalSeconds());
        result.put("skipQueryInTransaction", properties.isSkipQueryInTransaction());
        result.put("cacheDataSourcePrefix", properties.getCacheDataSourcePrefix());
        return result;
    }

    /**
     * 扫描容器中带物化注解的声明点（类级 + 方法级）。
     *
     * @return 声明点清单：beanName / className / method / classLevel / annotations / threshold / dataSource
     */
    public List<Map<String, Object>> routes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String beanName : applicationContext.getBeanNamesForType(Object.class, false, false)) {
            Class<?> beanType;
            try {
                beanType = applicationContext.getType(beanName, false);
            } catch (Exception e) {
                // 个别 bean 类型解析失败（FactoryBean 提前初始化等）直接跳过，不影响其余扫描
                continue;
            }
            if (beanType == null) {
                continue;
            }
            Class<?> targetClass = ClassUtils.getUserClass(beanType);
            MaterializedRoute classRoute = AnnotatedElementUtils.findMergedAnnotation(targetClass, MaterializedRoute.class);
            MaterializedRefresh classRefresh = AnnotatedElementUtils.findMergedAnnotation(targetClass, MaterializedRefresh.class);
            if (classRoute != null || classRefresh != null) {
                result.add(routeRow(beanName, targetClass, "", true, classRoute, classRefresh));
            }
            for (Method method : collectDeclaredMethods(targetClass)) {
                MaterializedRoute route = AnnotatedElementUtils.findMergedAnnotation(method, MaterializedRoute.class);
                MaterializedRefresh refresh = AnnotatedElementUtils.findMergedAnnotation(method, MaterializedRefresh.class);
                if (route != null || refresh != null) {
                    result.add(routeRow(beanName, targetClass, method.getName(), false, route, refresh));
                }
            }
        }
        result.sort(Comparator.comparing(row -> String.valueOf(row.get("className"))));
        return result;
    }

    /**
     * 按前缀命中的物化缓存数据源清单。
     *
     * <p>只列已注册且名字以 cacheDataSourcePrefix 开头的数据源；缓存键 = 去掉前缀后的
     * 「[源库#]表名」，与路由实现的命名规则一致。未列出的名字路由时不会命中、回落源库。</p>
     *
     * @return 缓存数据源清单：name / cacheKey / dataSourceClass
     */
    public List<Map<String, Object>> caches() {
        String prefix = MaterializedRouteProperties.bind(environment).getCacheDataSourcePrefix();
        Map<String, DataSource> all = DataSourceContextSupport.getAllDataSources();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, DataSource> entry : all.entrySet()) {
            String name = entry.getKey();
            if (prefix == null || !name.startsWith(prefix)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("cacheKey", name.substring(prefix.length()));
            row.put("dataSourceClass", entry.getValue() == null ? "" : entry.getValue().getClass().getName());
            result.add(row);
        }
        result.sort(Comparator.comparing(row -> String.valueOf(row.get("name"))));
        return result;
    }

    /**
     * 尝试触发物化副本刷新。
     *
     * @return 当前实现不具备主动刷新能力，恒返回 supported=false 与原因说明
     */
    public Map<String, Object> refresh() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", false);
        result.put("message", "当前物理化实现为安全降级骨架（写后刷新 no-op），未提供主动重建副本的能力；"
                + "副本数据仅由业务代码中 @MaterializedRefresh 标注的方法在调用时按注解阈值触发");
        return result;
    }

    /**
     * 汇总类层级中声明的全部方法（含父类，不含 Object）。
     */
    private List<Method> collectDeclaredMethods(Class<?> targetClass) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = targetClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method declared : current.getDeclaredMethods()) {
                methods.add(declared);
            }
        }
        return methods;
    }

    private Map<String, Object> routeRow(String beanName, Class<?> targetClass, String method,
                                         boolean classLevel, MaterializedRoute route, MaterializedRefresh refresh) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("beanName", beanName);
        row.put("className", targetClass.getName());
        row.put("method", method);
        row.put("classLevel", classLevel);
        List<String> annotations = new ArrayList<>();
        if (route != null) {
            annotations.add("MaterializedRoute");
        }
        if (refresh != null) {
            annotations.add("MaterializedRefresh");
        }
        row.put("annotations", annotations);
        row.put("threshold", route != null && route.threshold() > 0 ? route.threshold() : null);
        row.put("dataSource", route != null && !route.dataSource().isBlank() ? route.dataSource() : null);
        return row;
    }
}
