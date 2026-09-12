package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数组循环节点。
 *
 * <p>对来源数组逐条遍历，每次执行将当前元素写入 {@code as} 指定的属性并作为当前数据。
 * 循环结束（遍历完所有元素）时退出循环体，继续执行后续节点。</p>
 *
 * <p>循环通过图结构实现：节点执行后沿默认边进入循环体，循环体末尾回连到本节点；
 * 本节点按 {@link FlowContext#getExecuteCount(String)} 定位当前遍历下标。</p>
 *
 * <p>节点配置说明：</p>
 * <ul>
 *   <li>{@code source} 数组来源，当前数据 {@code current} 或上下文属性名，默认 current</li>
 *   <li>{@code as} 元素写入的上下文属性名，默认 item</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Component
public class SpiderSubLoopNode implements FlowNode {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderSubLoopNode.class);

    /**
     * 数组来源：当前数据
     */
    private static final String SOURCE_CURRENT = "current";

    /**
     * 元素属性默认名称
     */
    private static final String DEFAULT_AS = "item";

    /**
     * 节点类型标识。
     *
     * @return 节点类型
     */
    @Override
    public String type() {
        return "spider.subLoop";
    }

    /**
     * 节点配置表单元信息。
     *
     * @return 表单元信息列表
     */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.input("source", "数组来源"));
        schema.add(FlowNodeField.input("as", "元素属性名"));
        return schema;
    }

    /**
     * 执行循环节点。
     *
     * <p>按当前节点累计执行次数定位数组下标，将元素写入指定属性并作为当前数据；
     * 遍历完成时以 {@link FlowContext#exit()} 结束循环。</p>
     *
     * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String source = props.getString("source", SOURCE_CURRENT);
        String as = props.getString("as", DEFAULT_AS);

        List<?> items = resolveList(source, context);
        int index = context.getExecuteCount(context.getCurrentNodeId());
        if (index >= items.size()) {
            context.exit();
            log.info("spider.subLoop 循环结束，共 {} 条", items.size());
            return;
        }
        Object item = items.get(index);
        context.setAttribute(as, item);
        context.setData(item);
        log.info("spider.subLoop 第 {} 条: {}", index + 1, item);
    }

    /**
     * 解析数组来源。
     *
     * @param source  数组来源标识
     * @param context 当前流程上下文
     * @return 数组列表，为空时返回空列表
     */
    private List<?> resolveList(String source, FlowContext context) {
        Object value;
        if (SOURCE_CURRENT.equals(source)) {
            value = context.getData();
        } else {
            value = context.getAttribute(source);
        }
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                result.add(item);
            }
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(java.lang.reflect.Array.get(value, i));
            }
            return result;
        }
        /**
         * illegal参数异常。
         * @param source 源
         * @return illegal参数异常的结果
         */
        throw new IllegalArgumentException("spider.subLoop 数据来源不是数组: " + source);
    }
}