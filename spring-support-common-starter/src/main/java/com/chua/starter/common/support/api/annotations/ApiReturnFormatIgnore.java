package com.chua.starter.common.support.api.annotations;

import java.lang.annotation.*;

/**
 * 忽略统一返回格式包装注解
 * <p>
 * 标记此注解的接口将跳过统一返回值包装，直接返回原始数据。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>文件下载接口，需要返回原始二进制数据</li>
 *   <li>与第三方系统对接，需要特定的返回格式</li>
 *   <li>导出Excel/PDF等文件流接口</li>
 *   <li>图片/视频等媒体资源接口</li>
 *   <li>WebSocket或SSE推送接口</li>
 * </ul>
 *
 * <h3>配置要求</h3>
 * <pre>
 * plugin:
 *   api:
 *     uniform: true  # 需要开启统一返回值包装
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 方法级别：该接口返回原始数据
 * &#64;GetMapping("/download")
 * &#64;ApiReturnFormatIgnore
 * public byte[] downloadFile() {
 *     return fileBytes;  // 直接返回字节数组，不包装为 ReturnResult
 * }
 *
 * // 类级别：整个控制器都不进行包装
 * &#64;RestController
 * &#64;ApiReturnFormatIgnore
 * public class FileController {
 *     &#64;GetMapping("/image")
 *     public byte[] getImage() { ... }
 * }
 *
 * // 正常接口（自动包装）
 * &#64;GetMapping("/user")
 * public User getUser() {
 *     return user;  // 自动包装为 {"code": 200, "data": {...}}
 * }
 * </pre>
 *
 * @author CH
 * @since 2020-09-23
 * @version 1.0.0
 * @see com.chua.starter.common.support.api.response.ApiUniformResponseBodyAdvice
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiReturnFormatIgnore {
}

