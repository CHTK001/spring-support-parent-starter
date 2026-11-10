package com.chua.starter.device.provider.sdk;

/**
 * 平台接口版本描述。
 *
 * <p><b>为什么单独建模</b>：各平台接口是分版本演进的，且同一平台不同版本的路径/字段差异很大。
 * 实测例子：海康云曜 {@code /api/eits/v1/global/device/page} 可用，
 * 而 {@code /api/eits/v2/global/device/page} 直接返回 400；
 * 大华综合安防设备分页是 {@code /evo-apigw/evo-brm/1.2.0/device/subsystem/page}，
 * 设备详情却是 {@code 1.0.0}。</p>
 *
 * <p>因此每个平台实现都要显式声明「当前按哪一版文档实现」，并允许凭据级覆盖
 * （{@code DeviceConnectInfo.extra("apiVersion")}），换版本时不用改代码。</p>
 *
 * @param version  版本标识，如 {@code v1} / {@code 1.2.0}
 * @param docUrl   官方文档地址（用于核对，不参与运行）
 * @param verified 该版本是否已用真实凭据验证通过
 * @since 4.0.0.51
 * @author CH
 */
public record PlatformApiVersion(
        String version,
        String docUrl,
        boolean verified
) {

    /**
     * 构造一个已核实的版本。
     *
     * @param version 版本标识
     * @param docUrl  官方文档地址
     * @return 版本描述
     */
    public static PlatformApiVersion verified(String version, String docUrl) {
        return new PlatformApiVersion(version, docUrl, true);
    }

    /**
     * 构造一个文档已确认、但尚未用真实凭据跑通的版本。
     *
     * @param version 版本标识
     * @param docUrl  官方文档地址
     * @return 版本描述
     */
    public static PlatformApiVersion documented(String version, String docUrl) {
        return new PlatformApiVersion(version, docUrl, false);
    }
}
