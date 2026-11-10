package com.chua.starter.device.provider.sdk;

/**
 * 一条平台接口的声明（来自官方文档）。
 *
 * <p>门面不只做「连通性测试」和几个固定方法，而是把平台**全部**接口登记成目录：
 * 前端/业务按 {@code code} 调用 {@code DevicePlatformSdk#invoke}，就能打到任意官方接口，
 * 不必为每个新接口改一次 Java 代码。目录同时也是能力清单，前端据此渲染入口。</p>
 *
 * @param code    接口编码（稳定标识，如 {@code device.page}）
 * @param name    中文名（官方文档标题）
 * @param method  HTTP 方法
 * @param path    接口路径（含版本前缀，与官方文档一致）
 * @param version 该接口所属版本
 * @param domain  能力域：device / monitor / org / person / access / alarm / video / other
 * @param auth    认证方式：GLOBAL（全局 token/签名）/ USER（用户 token）/ NONE
 * @since 4.0.0.51
 * @author CH
 */
public record PlatformApiSpec(
        String code,
        String name,
        String method,
        String path,
        String version,
        String domain,
        Auth auth
) {

    /** 接口认证方式 */
    public enum Auth {
        /** AK/SK 换取的全局 token 或签名即可调用 */
        GLOBAL,
        /** 需要用户登录 token（云曜等平台的组织/设备非 global 接口） */
        USER,
        /** 无需认证（换 token、挑战码等） */
        NONE
    }

    /**
     * 构造全局认证接口。
     *
     * @param code    编码
     * @param name    中文名
     * @param method  方法
     * @param path    路径
     * @param version 版本
     * @param domain  能力域
     * @return 声明
     */
    public static PlatformApiSpec global(String code, String name, String method, String path,
                                         String version, String domain) {
        return new PlatformApiSpec(code, name, method, path, version, domain, Auth.GLOBAL);
    }

    /**
     * 构造需要用户 token 的接口。
     *
     * @param code    编码
     * @param name    中文名
     * @param method  方法
     * @param path    路径
     * @param version 版本
     * @param domain  能力域
     * @return 声明
     */
    public static PlatformApiSpec user(String code, String name, String method, String path,
                                       String version, String domain) {
        return new PlatformApiSpec(code, name, method, path, version, domain, Auth.USER);
    }
}
