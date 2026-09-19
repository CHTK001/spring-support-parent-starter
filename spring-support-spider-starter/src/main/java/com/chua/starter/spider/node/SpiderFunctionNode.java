package com.chua.starter.spider.node;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowProps;
import com.chua.common.support.utils.ClassUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 函数调用节点。
 *
 * <p>通过 Spring Bean 名称与方法名，调用任意无参或有参方法，并将返回值写入上下文属性。
 * 参数从上下文属性中按名称解析，支持 {@code current} 引用当前处理数据。</p>
 *
 * <p>节点配置说明：</p>
 * <ul>
 *   <li>{@code beanName} Spring Bean 名称（必填）</li>
 *   <li>{@code methodName} 方法名（必填）</li>
 *   <li>{@code params} 方法参数取值来源，顺序对应方法参数位置，
 *       每一项为上下文属性名（逗号分隔），{@code current} 表示当前处理数据</li>
 *   <li>{@code saveAs} 返回值写入的上下文属性名（必填）</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class SpiderFunctionNode implements FlowNode {

    /**
     * Spring 容器
     */
    @Resource
    /** Application上下文 */
    private ApplicationContext applicationContext;

    /**
    * 节点类型标识。
    *
    * @return 节点类型
    */
    @Override
    public String type() {
        return "spider.function";
    }

    /**
     * 节点配置表单元信息。
     *
     * @return 表单元信息列表
     */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.input("beanName", "Bean 名称", true));
        schema.add(FlowNodeField.input("methodName", "方法名", true));
        schema.add(FlowNodeField.input("params", "方法参数（属性名，逗号分隔）"));
        schema.add(FlowNodeField.input("saveAs", "写入属性名", true));
        return schema;
    }

    /**
     * 执行函数调用节点。
     *
     * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String beanName = props.getString("beanName");
        String methodName = props.getString("methodName");
        String saveAs = props.getString("saveAs");
        if (beanName == null || beanName.isEmpty()) {
            throw new IllegalArgumentException("spider.function 节点缺少 beanName 参数");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("spider.function 节点缺少 methodName 参数");
        }
        if (saveAs == null || saveAs.isEmpty()) {
            throw new IllegalArgumentException("spider.function 节点缺少 saveAs 参数");
        }
        Object bean = applicationContext.getBean(beanName);
        List<Object> args = resolveParams(props.getString("params"), context);
        Object result = invokeMethod(bean, methodName, args);
        context.setAttribute(saveAs, result);
        log.info("spider.function 调用完成: bean={} method={} saveAs={}", beanName, methodName, saveAs);
    }

    /**
     * 解析方法参数值列表。
     *
     * @param paramsText 属性名列表（逗号分隔）
     * @param context    当前流程上下文
     * @return 参数值列表
     */
    private List<Object> resolveParams(String paramsText, FlowContext context) {
        List<Object> result = new ArrayList<>();
        if (paramsText == null || paramsText.trim().isEmpty()) {
            return result;
        }
        for (String token : paramsText.split(",")) {
            String name = token.trim();
            if ("current".equals(name)) {
                result.add(context.getData());
            } else {
                result.add(context.getAttribute(name));
            }
        }
        return result;
    }

    /**
     * 通过 reflect工具 调用方法。
     *
     * <p>按参数类型自动匹配最合适的方法：优先精确匹配参数类型，
     * 若无匹配则尝试按顺序匹配参数类型兼容的方法。若方法抛出异常则直接向上抛出。</p>
     *
     * @param bean       目标 Bean 实例
     * @param methodName 方法名
     * @param args       实参列表
     * @return 方法返回值
     */
    private Object invokeMethod(Object bean, String methodName, List<Object> args) {
        Class<?> clazz = bean.getClass();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != args.size()) {
                continue;
            }
            boolean match = true;
            Object[] converted = new Object[args.size()];
            for (int i = 0; i < paramTypes.length; i++) {
                Object arg = args.get(i);
                if (arg == null) {
                    converted[i] = null;
                    continue;
                }
                Class<?> targetType = paramTypes[i];
                if (!targetType.isAssignableFrom(arg.getClass())) {
                    if (targetType.isPrimitive()) {
                        targetType = wrapPrimitive(targetType);
                        if (!targetType.isAssignableFrom(arg.getClass())) {
                            match = false;
                            break;
                        }
                    } else {
                        match = false;
                        break;
                    }
                }
                converted[i] = arg;
            }
            if (match) {
                MethodHandle handle = ReflectUtils.findMethodHandle(clazz, methodName, method.getReturnType(), paramTypes);
                if (handle == null) {
                    throw new IllegalStateException("spider.function 未找到匹配的方法: " + methodName);
                }
                try {
                    if (Modifier.isStatic(method.getModifiers())) {
                        return handle.invokeWithArguments(converted);
                    }
                    return handle.bindTo(bean).invokeWithArguments(converted);
                } catch (Throwable e) {
                    throw new IllegalStateException("spider.function 调用失败: " + e.getMessage(), e);
                }
            }
        }
        throw new IllegalStateException("spider.function 未找到匹配的方法: " + methodName);
    }

    /**
     * 将原始类型包装为其对应的包装类型。
     *
     * @param primitive 原始类型
     * @return 包装类型
     */
    private Class<?> wrapPrimitive(Class<?> primitive) {
        // [P3C 四十二] 改走 ClassUtils.fromPrimitive，删除手写 9 个 if 分支
        return ClassUtils.fromPrimitive(primitive);
    }
}
