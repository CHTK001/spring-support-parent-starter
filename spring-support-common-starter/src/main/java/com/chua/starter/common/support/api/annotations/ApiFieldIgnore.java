package com.chua.starter.common.support.api.annotations;

import com.chua.starter.common.support.api.rule.ApiIgnoreSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * 字段条件忽略注解
 * <p>
 * 用于根据分组条件控制字段是否在响应中输出。
 * 不同的API接口可以返回同一实体的不同字段子集。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>列表接口和详情接口返回不同字段</li>
 *   <li>不同角色用户看到不同的字段</li>
 *   <li>简化版API和完整版API复用同一实体</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 定义分组接口
 * public interface ListGroup {}
 * public interface DetailGroup {}
 *
 * public class UserVO {
 *     private String name;
 *
 *     // 在列表查询时忽略此字段
 *     &#64;ApiFieldIgnore(ListGroup.class)
 *     private String detailInfo;
 *
 *     // 在列表和详情查询时都忽略
 *     &#64;ApiFieldIgnore({ListGroup.class, DetailGroup.class})
 *     private String internalData;
 * }
 *
 * // 控制器使用
 * &#64;GetMapping("/list")
 * &#64;ApiGroup(ListGroup.class)  // 激活ListGroup 分组
 * public List&lt;UserVO&gt; list() { ... }
 *
 * &#64;GetMapping("/detail")
 * &#64;ApiGroup(DetailGroup.class)  // 激活DetailGroup 分组
 * public UserVO detail() { ... }
 * </pre>
 *
 * @author CH
 * @since 2025/1/1
 * @version 1.0.0
 * @see ApiGroup
 * @see ApiIgnoreSerializer
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = ApiIgnoreSerializer.class)
public @interface ApiFieldIgnore {

    /**
     * 忽略分组
     * <p>
     * 指定在哪些分组下该字段应被忽略。
     * 当请求激活的分组与此处配置的分组匹配时，字段不会被序列化输出。
     * </p>
     *
     * @return 分组类数组
     */
    Class<?>[] value();
}

