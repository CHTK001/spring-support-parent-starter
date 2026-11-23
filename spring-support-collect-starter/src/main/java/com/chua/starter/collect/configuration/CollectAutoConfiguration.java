package com.chua.starter.collect.configuration;

import com.chua.starter.collect.mapper.CollectRecordMapper;
import com.chua.starter.collect.mapper.CollectTaskLogMapper;
import com.chua.starter.collect.mapper.CollectTaskMapper;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.service.CollectFieldService;
import com.chua.starter.collect.store.CollectDatabaseSinkFactory;
import com.chua.starter.collect.store.CollectRecordSearchIndex;
import com.chua.starter.collect.store.CollectSinkFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 采集模块自动装配：把采集能力做成「加依赖即生效」的独立模块。
 *
 * <p>本 starter 承载采集侧的全部能力：执行引擎（engine）、业务服务（service）、
 * Mapper 与实体（mapper / entity）、对外接口（controller）、通知（notify）、
 * 任务仓储（store），以及随包携带的建表脚本（{@code db/init/**}）。</p>
 *
 * <p>与同仓库其它 starter 的差异：它们只把自动装配类登记进
 * {@code META-INF/spring/...AutoConfiguration.imports}，Mapper 扫描交给消费方自己写
 * {@code @MapperScan}（例如 job-starter 只暴露 {@link #MAPPER_PACKAGE} 常量）。
 * 采集模块是给其它业务系统整体集成的（后续要承接 eduai 的采集），所以这里把
 * 组件扫描与 Mapper 扫描一并内置，消费方只需：</p>
 *
 * <pre>
 * 1) 依赖本 starter
 * 2) 需要时用 plugin.collect.enable=false 关闭（默认开，与 {@link CollectProperties} 一致）
 * </pre>
 *
 * <p>若消费方更希望显式控制扫描范围，可用本类的 {@link #BASE_PACKAGE} /
 * {@link #MAPPER_PACKAGE} 自行 {@code @ComponentScan} / {@code @MapperScan}；
 * 重复注册同一批类不会冲突（同名同类视为兼容定义，会被跳过）。</p>
 *
 * <p>建表与种子数据仍由 {@code plugin.datasource.script} 以
 * {@code classpath*:db/init/*.sql} 扫描执行（ONCE 语义：空库才建，已有库不动结构），
 * 本自动装配不重复承担 DDL 职责。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = CollectProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CollectProperties.class)
@ComponentScan(
        basePackages = CollectAutoConfiguration.BASE_PACKAGE,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = CollectAutoConfiguration.class))
@MapperScan(CollectAutoConfiguration.MAPPER_PACKAGE)
public class CollectAutoConfiguration {

    /**
     * 采集模块的根包，消费方自行扫描时使用。
     */
    public static final String BASE_PACKAGE = "com.chua.starter.collect";

    /**
     * 采集模块的 Mapper 包，消费方自行扫描时使用。
     */
    public static final String MAPPER_PACKAGE = "com.chua.starter.collect.mapper";

    /**
     * 采集结果落库通道工厂：默认按 {@code plugin.collect.persistence.type} 提供数据库落库，
     * 宿主注册自己的 {@code CollectSinkFactory} Bean 即可整体替换持久化实现。
     *
     * @param taskLogMapper       节点日志表访问
     * @param recordMapper        采集结果表访问
     * @param taskMapper          任务表访问
     * @param fieldService        字段定义服务
     * @param properties          平台配置
     * @param searchIndexProvider 结果全文索引提供者（未启用时为空）
     * @return 落库通道工厂
     */
    @Bean
    @ConditionalOnMissingBean(CollectSinkFactory.class)
    public CollectSinkFactory collectSinkFactory(CollectTaskLogMapper taskLogMapper,
                                                 CollectRecordMapper recordMapper,
                                                 CollectTaskMapper taskMapper,
                                                 CollectFieldService fieldService,
                                                 CollectProperties properties,
                                                 ObjectProvider<CollectRecordSearchIndex> searchIndexProvider) {
        return new CollectDatabaseSinkFactory(taskLogMapper, recordMapper, taskMapper, fieldService,
                properties, searchIndexProvider);
    }
}
