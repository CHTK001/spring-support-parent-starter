package com.chua.starter.spider.config;

import com.chua.common.support.task.flow.FlowNodeRegistry;
import com.chua.starter.spider.node.SpiderConditionNode;
import com.chua.starter.spider.node.SpiderDbNode;
import com.chua.starter.spider.node.SpiderExtractNode;
import com.chua.starter.spider.node.SpiderFunctionNode;
import com.chua.starter.spider.node.SpiderMapNode;
import com.chua.starter.spider.node.SpiderNoopNode;
import com.chua.starter.spider.node.SpiderOutputNode;
import com.chua.starter.spider.node.SpiderRequestNode;
import com.chua.starter.spider.node.SpiderScriptNode;
import com.chua.starter.spider.node.SpiderSubLoopNode;
import com.chua.starter.spider.node.SpiderTransformNode;
import com.chua.starter.spider.trace.SpiderTraceFilter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 爬虫中心自动配置。
 *
 * <p>负责：组件扫描（service / controller / mapper）、全链路追踪过滤器，
 * 以及将 10 个爬虫节点（request/extract/transform/subLoop/map/output/condition/script/db/function）
 * 注册到 {@link FlowNodeRegistry}，供流程编排与节点清单使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.chua.starter.mybatis.MybatisPlusConfiguration")
@ConditionalOnProperty(prefix = "spider", name = "enable", havingValue = "true", matchIfMissing = true)
@MapperScan("com.chua.starter.spider.mapper")
@ComponentScan("com.chua.starter.spider")
@EnableScheduling
@EnableCaching
public class SpiderAutoConfiguration {

    /**
     * traceId 过滤器注册顺序
     */
    private static final int TRACE_FILTER_ORDER = 0;

    /**
     * 请求节点
     */
    private final SpiderRequestNode requestNode;

    /**
     * 提取节点
     */
    private final SpiderExtractNode extractNode;

    /**
     * 转换节点
     */
    private final SpiderTransformNode transformNode;

    /**
     * 循环节点
     */
    private final SpiderSubLoopNode subLoopNode;

    /**
     * 映射节点
     */
    private final SpiderMapNode mapNode;

    /**
     * 落库节点
     */
    private final SpiderOutputNode outputNode;

    /**
     * 条件节点
     */
    private final SpiderConditionNode conditionNode;

    /**
     * 脚本节点
     */
    private final SpiderScriptNode scriptNode;

    /**
     * 数据库节点
     */
    private final SpiderDbNode dbNode;

    /**
     * 函数调用节点
     */
    private final SpiderFunctionNode functionNode;

    /**
     * 占位节点（兜底，用于未识别类型或后续扩展）
     */
    private final SpiderNoopNode noopNode;

    /**
     * 构造器注入 11 个爬虫节点。
     *
     * @param requestNode   请求节点
     * @param extractNode   提取节点
     * @param transformNode 转换节点
     * @param subLoopNode   循环节点
     * @param mapNode       映射节点
     * @param outputNode    落库节点
     * @param conditionNode 条件节点
     * @param scriptNode    脚本节点
     * @param dbNode        数据库节点
     * @param functionNode  函数调用节点
     * @param noopNode      占位节点
     */
    public SpiderAutoConfiguration(SpiderRequestNode requestNode,
                                   SpiderExtractNode extractNode,
                                   SpiderTransformNode transformNode,
                                   SpiderSubLoopNode subLoopNode,
                                   SpiderMapNode mapNode,
                                   SpiderOutputNode outputNode,
                                   SpiderConditionNode conditionNode,
                                   SpiderScriptNode scriptNode,
                                   SpiderDbNode dbNode,
                                   SpiderFunctionNode functionNode,
                                   SpiderNoopNode noopNode) {
        this.requestNode = requestNode;
        this.extractNode = extractNode;
        this.transformNode = transformNode;
        this.subLoopNode = subLoopNode;
        this.mapNode = mapNode;
        this.outputNode = outputNode;
        this.conditionNode = conditionNode;
        this.scriptNode = scriptNode;
        this.dbNode = dbNode;
        this.functionNode = functionNode;
        this.noopNode = noopNode;
    }

    /**
     * 注册爬虫节点到流程节点注册中心。
     *
     * <p>节点统一使用 {@code spider.} 前缀注册，供前端节点清单展示与流程 JSON 导入匹配；
     * script/db/function 已绑定到真实节点实现，{@link SpiderNoopNode} 仅作为兜底。</p>
     */
    @PostConstruct
    public void registerSpiderNodes() {
        FlowNodeRegistry.register(requestNode.type(), requestNode, "HTTP 请求");
        FlowNodeRegistry.register(extractNode.type(), extractNode, "字段提取");
        FlowNodeRegistry.register(transformNode.type(), transformNode, "转换处理");
        FlowNodeRegistry.register(subLoopNode.type(), subLoopNode, "数组循环");
        FlowNodeRegistry.register(mapNode.type(), mapNode, "字段映射");
        FlowNodeRegistry.register(outputNode.type(), outputNode, "数据落库");
        FlowNodeRegistry.register(conditionNode.type(), conditionNode, "条件分支");
        FlowNodeRegistry.register(scriptNode.type(), scriptNode, "脚本（SpEL）");
        FlowNodeRegistry.register(dbNode.type(), dbNode, "数据库");
        FlowNodeRegistry.register(functionNode.type(), functionNode, "函数调用");
        FlowNodeRegistry.register(noopNode.type(), noopNode, "占位节点");
    }

    /**
     * 注册全链路追踪过滤器 Bean。
     *
     * <p>覆盖所有路径，为请求注入 traceId 到 MDC 与响应头。</p>
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnWebApplication
    public FilterRegistrationBean<Filter> spiderTraceFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SpiderTraceFilter());
        registration.addUrlPatterns("/*");
        registration.setName("spiderTraceFilter");
        registration.setOrder(TRACE_FILTER_ORDER);
        return registration;
    }
}