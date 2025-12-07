package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * API分组注解
 * <p>
 * 用于在Controller方法上声明当前请求激活的分组。
 * 与@ApiFieldIgnore 配合实现字段的条件输出。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>同一实体在不同接口返回不同字段</li>
 *   <li>列表接口返回精简字段，详情接口返回完整字段</li>
 *   <li>根据业务场景动态控制响应内容</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 定义分组接口
 * public interface ListGroup {}
 * public interface DetailGroup {}
 *
 * // 实体定义
 * public class UserVO {
 *     private String name;
 *
 *     &#64;ApiFieldIgnore(ListGroup.class)  // 列表时忽略
 *     private String address;
 * }
 *
 * // 控制器
 * &#64;RestController
 * public class UserController {
 *
 *     &#64;GetMapping("/list")
 *     &#64;ApiGroup(ListGroup.class)  // 激活列表分组
 *     public List&lt;UserVO&gt; list() {
 *         // 返回的UserVO 不包含address 字段
 *     }
 *
 *     &#64;GetMapping("/detail")
 *     &#64;ApiGroup(DetailGroup.class)  // 激活详情分组
 *     public UserVO detail() {
 *         // 返回完整的UserVO，包含 address 字段
 *     }
 * }
 * </pre>
 *
 * @author CH
 * @since 2025/1/1
 * @version 1.0.0
 * @see ApiFieldIgnore
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiGroup {

    /**
     * 激活的分组
     * <p>
     * 指定当前接口激活的分组类。
     * 与@ApiFieldIgnore 标记且包含此分组的字段将被忽略。
     * </p>
     *
     * @return 分组类数组
     */
    Class<?>[] value();
}

