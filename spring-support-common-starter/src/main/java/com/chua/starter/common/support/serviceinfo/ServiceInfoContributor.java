package com.chua.starter.common.support.serviceinfo;

/**
* 服务信息 贡献者接口。
*
* <p>各 starter 通过实现此接口并声明为 Spring Bean，
* 在容器初始化时自动向 {@link ServiceInfoRegistry} 注册端点和开关信息。</p>
*
* <h3>使用示例</h3>
* <pre>{@code
* @Bean
* public ServiceInfoContributor myContributor() {
*     return registry -> {
*         registry.registerEndpoint("我的分组", "我的端点", "/api/foo", "功能描述");
*         registry.registerFlag("my-flag", "我的分组", true, "开关说明");
*     };
* }
* }</pre>分组", true, "开关说明");
*     };
* }
* }</pre>
*
* @since 4.0.0.42
* @author CH
 */
@FunctionalInterface
public interface ServiceInfoContributor {

    /**
    * 向注册表贡献端点和开关信息。
    *
    * @param registry 服务信息 注册表
     */
    void contribute(ServiceInfoRegistry registry);
}
