package com.chua.starter.datasource.support;

import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 数据库文件提供者接口
 * <p>
 * 用于各模块提供数据库初始化脚本的SPI接口。
 * 模块可通过实现此接口并在 META-INF/services 中注册，
 * 使FlywayLikePopulator自动发现并加载模块的数据库脚本。
 * </p>
 *
 * <h3>使用方式:</h3>
 * <ol>
 *   <li>实现此接口</li>
 *   <li>在 META-INF/services/com.chua.starter.datasource.support.DatabaseFileProvider 文件中注册实现类</li>
 * </ol>
 *
 * <h3>示例实现:</h3>
 * <pre>
 * public class MyModuleDatabaseFileProvider implements DatabaseFileProvider {
 *
 *     &#64;Override
 *     public boolean isSupported() {
 *         // 检查模块是否启用
 *         return true;
 *     }
 *
 *     &#64;Override
 *     public List&lt;Resource&gt; getResources() {
 *         PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
 *         return Arrays.asList(resolver.getResources("classpath*:db/init/*.sql"));
 *     }
 *
 *     &#64;Override
 *     public int getOrder() {
 *         return 100; // 执行顺序，值越小越先执行
 *     }
 * }
 * </pre>
 *
 * @author CH
 * @since 2025/12/20
 * @version 1.0
 */
public interface DatabaseFileProvider {

    /**
     * 判断当前提供者是否支持/启用
     * <p>
     * 可以根据配置或环境判断是否需要加载该模块的数据库脚本
     * </p>
     *
     * @return true-支持/启用，false-不支持/禁用
     */
    boolean isSupported();

    /**
     * 获取数据库脚本资源列表
     * <p>
     * 返回模块需要执行的数据库初始化/迁移脚本资源。
     * 脚本应遵循命名规范: V版本号__描述.sql
     * </p>
     *
     * @return 脚本资源列表，不能为null（可返回空列表）
     */
    List<Resource> getResources();

    /**
     * 获取执行优先级
     * <p>
     * 值越小优先级越高，越先执行。
     * 建议值范围:
     * <ul>
     *   <li>0-100: 核心基础模块</li>
     *   <li>100-500: 通用业务模块</li>
     *   <li>500-1000: 扩展模块</li>
     * </ul>
     * </p>
     *
     * @return 优先级值，默认100
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 获取提供者名称
     * <p>
     * 用于日志输出和调试
     * </p>
     *
     * @return 提供者名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
