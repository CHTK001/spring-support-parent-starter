/**
 * API 控制注解包
 * <p>
 * 提供一组用于控制 API 接口行为的注解，包括访问控制、版本控制、功能开关等。
 * </p>
 *
 * <h2>注解列表及优先级</h2>
 * <p>
 * 拦截器按以下优先级顺序处理注解（数字越小优先级越高）：
 * </p>
 *
 * <table border="1">
 *   <tr><th>优先级</th><th>注解</th><th>阶段</th><th>功能描述</th></tr>
 *   <tr><td>1</td><td>@ApiInternal</td><td>拦截器</td><td>内部接口访问控制，校验IP/服务白名单，设置跳过鉴权标识</td></tr>
 *   <tr><td>2</td><td>@ApiFeature</td><td>拦截器</td><td>功能开关控制，运行时动态启用/禁用接口</td></tr>
 *   <tr><td>3</td><td>@ApiMock</td><td>拦截器</td><td>Mock 数据返回，开发/测试环境返回模拟数据</td></tr>
 *   <tr><td>4</td><td>@ApiDeprecated</td><td>拦截器</td><td>接口废弃控制，根据版本返回废弃提示或空结果</td></tr>
 *   <tr><td>5</td><td>@ApiProfile</td><td>映射注册</td><td>环境控制，根据 Spring Profile 决定接口是否注册</td></tr>
 *   <tr><td>6</td><td>@ApiPlatform</td><td>映射注册</td><td>平台控制，根据平台类型决定接口是否注册</td></tr>
 *   <tr><td>7</td><td>@ApiVersion</td><td>映射注册</td><td>版本控制，为接口添加版本号路径前缀</td></tr>
 * </table>
 *
 * <h2>处理阶段说明</h2>
 * <ul>
 *   <li><b>映射注册阶段</b>：在 Spring MVC 启动时处理，决定接口是否注册到路由表</li>
 *   <li><b>拦截器阶段</b>：在请求进入 Controller 前处理，决定是否放行请求</li>
 * </ul>
 *
 * <h2>注解详细说明</h2>
 *
 * <h3>1. @ApiInternal（优先级：1）</h3>
 * <ul>
 *   <li><b>功能</b>：标记接口为内部接口，仅允许内网IP或白名单访问</li>
 *   <li><b>特性</b>：自动跳过 OAuth 鉴权（skipAuth=true）</li>
 *   <li><b>内网IP</b>：127.x.x.x、10.x.x.x、172.16-31.x.x、192.168.x.x</li>
 *   <li><b>白名单</b>：支持精确IP、通配符（192.168.1.*）、CIDR（192.168.1.0/24）</li>
 *   <li><b>服务白名单</b>：通过请求头 X-Service-Name 识别</li>
 * </ul>
 *
 * <h3>2. @ApiFeature（优先级：2）</h3>
 * <ul>
 *   <li><b>功能</b>：功能开关控制，支持运行时动态开启/关闭接口</li>
 *   <li><b>管理接口</b>：/api/features（可配置）</li>
 *   <li><b>分组</b>：支持按 group 分组管理</li>
 *   <li><b>配置</b>：plugin.api.feature.enable=true</li>
 * </ul>
 *
 * <h3>3. @ApiMock（优先级：3）</h3>
 * <ul>
 *   <li><b>功能</b>：开发/测试环境返回 Mock 数据</li>
 *   <li><b>环境控制</b>：通过 profile 属性指定生效环境</li>
 *   <li><b>数据来源</b>：response 属性（JSON字符串）或 responseFile（classpath文件）</li>
 *   <li><b>配置</b>：plugin.api.mock.enable=true（全局开关）</li>
 * </ul>
 *
 * <h3>4. @ApiDeprecated（优先级：4）</h3>
 * <ul>
 *   <li><b>功能</b>：标记接口废弃，控制版本兼容性</li>
 *   <li><b>since</b>：废弃起始版本</li>
 *   <li><b>replacement</b>：替代接口路径（未配置则请求版本>=since时返回空）</li>
 *   <li><b>removedIn</b>：完全移除版本（返回 410 Gone）</li>
 *   <li><b>响应头</b>：自动添加 X-API-Deprecated 等警告信息</li>
 * </ul>
 *
 * <h3>5. @ApiProfile（优先级：5）</h3>
 * <ul>
 *   <li><b>功能</b>：根据 Spring Profile 环境控制接口注册</li>
 *   <li><b>处理时机</b>：启动时决定是否注册接口</li>
 *   <li><b>配置</b>：spring.profiles.active</li>
 * </ul>
 *
 * <h3>6. @ApiPlatform（优先级：6）</h3>
 * <ul>
 *   <li><b>功能</b>：根据平台类型控制接口注册</li>
 *   <li><b>处理时机</b>：启动时决定是否注册接口</li>
 *   <li><b>平台类型</b>：SYSTEM、TENANT、MONITOR、SCHEDULER、OAUTH</li>
 *   <li><b>配置</b>：plugin.api.platform.name 或 plugin.api.platform.alias-name</li>
 * </ul>
 *
 * <h3>7. @ApiVersion（优先级：7）</h3>
 * <ul>
 *   <li><b>功能</b>：API 版本控制，为接口添加版本号路径</li>
 *   <li><b>处理时机</b>：启动时修改接口路径</li>
 *   <li><b>示例</b>：@ApiVersion(2) 将 /users 映射为 /v2/users</li>
 *   <li><b>配置</b>：plugin.api.version.enable=true</li>
 * </ul>
 *
 * <h2>配置示例</h2>
 * <pre>
 * plugin:
 *   api:
 *     version:
 *       enable: true
 *     platform:
 *       enable: true
 *       name: SYSTEM
 *     mock:
 *       enable: true
 *     feature:
 *       enable: true
 *       path: /api/features
 *     internal:
 *       enable: true
 *       global-allowed-ips:
 *         - 192.168.1.100
 *       global-allowed-services:
 *         - monitor-service
 * </pre>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
package com.chua.starter.common.support.api.annotations;
