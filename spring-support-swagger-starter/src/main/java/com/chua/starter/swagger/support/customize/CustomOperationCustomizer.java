package com.chua.starter.swagger.support.customize;

import com.chua.starter.swagger.support.Knife4jProperties;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import com.github.xiaoymin.knife4j.core.conf.ExtensionsConstants;
import com.github.xiaoymin.knife4j.core.util.StrUtil;
import com.github.xiaoymin.knife4j.extend.util.ExtensionUtils;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * 操作定制器
 * 用于处理Knife4j的ApiOperationSupport和ApiSupport注解，支持作者和排序
 *
 * @author CH
 * @since 2025/5/27 13:37
 */
public class CustomOperationCustomizer implements GlobalOperationCustomizer {

    private final Knife4jProperties knife4jProperties;
    private final Map<String, String> defaultHeaders;

    /**
     * 构造函数
     *
     * @param knife4jProperties Knife4j配置属性
     */
    public CustomOperationCustomizer(Knife4jProperties knife4jProperties) {
        this.knife4jProperties = knife4jProperties;
        this.defaultHeaders = Optional.ofNullable(knife4jProperties.getDefaultHeader())
                .orElse(Collections.emptyMap());
    }

    /**
     * 定制操作
     * 解析支持作者、接口排序
     * 参考：https://gitee.com/xiaoym/knife4j/issues/I6FB9I
     *
     * @param operation 操作对象
     * @param handlerMethod 处理器方法
     * @return 定制后的操作对象
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiOperationSupport operationSupport = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiOperationSupport.class);
        if (operationSupport != null) {
            String author = ExtensionUtils.getAuthors(operationSupport);
            if (StrUtil.isNotBlank(author)) {
                operation.addExtension(ExtensionsConstants.EXTENSION_AUTHOR, author);
            }
            if (operationSupport.order() != 0) {
                operation.addExtension(ExtensionsConstants.EXTENSION_ORDER, operationSupport.order());
            }
        } else {
            // 如果方法级别不存在，再找一次class级别的
            ApiSupport apiSupport = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), ApiSupport.class);
            if (apiSupport != null) {
                String author = ExtensionUtils.getAuthor(apiSupport);
                if (StrUtil.isNotBlank(author)) {
                    operation.addExtension(ExtensionsConstants.EXTENSION_AUTHOR, author);
                }
                if (apiSupport.order() != 0) {
                    operation.addExtension(ExtensionsConstants.EXTENSION_ORDER, apiSupport.order());
                }
            }
        }
        return operation;
    }
}
