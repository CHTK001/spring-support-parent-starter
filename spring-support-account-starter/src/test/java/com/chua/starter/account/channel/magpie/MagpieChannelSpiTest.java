// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.channel.ChannelUpstreamAdmin;
import com.chua.starter.common.support.channel.ChannelException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * magpie 三个渠道实现的 SPI 注册回归。
 *
 * <p>实现已从 AI 模块迁到账号模块，登记文件也随之拆成两个 jar 里的同名资源
 * （{@code META-INF/extensions/<SPI>}）。SPI 框架用
 * {@code ClassLoader#getResources} 枚举 <b>全部</b> 同名资源再逐行合并，
 * 所以这里必须验证：账号侧注册的 {@code magpie} 别名确实能被解析出来，
 * 否则渠道页会在运行期退化成「未注册的渠道」。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
class MagpieChannelSpiTest {

    @Test
    @DisplayName("@Spi(magpie) 的鉴权实现可被 SPI 解析")
    void authManagerIsDiscoverable() {
        AccountChannelAuthManager manager = ServiceProvider.of(AccountChannelAuthManager.class)
                .getNewExtension("magpie");

        assertNotNull(manager, "magpie 鉴权实现未注册：检查 META-INF/extensions 登记文件");
        assertEquals("magpie", manager.channel());
        assertTrue(manager.supportsCheckin(), "magpie 的签到由网关管理面代劳，应声明支持");
    }

    @Test
    @DisplayName("@Spi(magpie) 的积分实现可被 SPI 解析")
    void creditProviderIsDiscoverable() {
        ChannelCreditProvider provider = ServiceProvider.of(ChannelCreditProvider.class)
                .getNewExtension("magpie");

        assertNotNull(provider, "magpie 积分实现未注册：检查 META-INF/extensions 登记文件");
        assertEquals("magpie", provider.channel());
        assertTrue(provider.supportsCredit());
    }

    @Test
    @DisplayName("@Spi(magpie) 的上游管理实现可被 SPI 解析，并只接管 magpie")
    void upstreamAdminIsDiscoverable() {
        ChannelUpstreamAdmin admin = ServiceProvider.of(ChannelUpstreamAdmin.class)
                .getNewExtension("magpie");

        assertNotNull(admin, "magpie 上游管理实现未注册：检查 META-INF/extensions 登记文件");
        assertEquals("magpie", admin.channel());
        assertTrue(admin.supports("MAGPIE"));
        assertFalse(admin.supports("cline"));
    }

    @Test
    @DisplayName("不支持的动作与空 id 直接以 INVALID_PARAM 拒绝，不打上游")
    void invalidInputIsRejectedBeforeUpstream() {
        ChannelUpstreamAdmin admin = new MagpieUpstreamAdmin();

        ChannelException action = assertThrows(ChannelException.class,
                () -> admin.action(Map.of(), "delete", Map.of()));
        assertEquals(ChannelException.CODE_INVALID_PARAM, action.getCode());

        ChannelException deletion = assertThrows(ChannelException.class,
                () -> admin.delete(Map.of(), "  "));
        assertEquals(ChannelException.CODE_INVALID_PARAM, deletion.getCode());
    }

    @Test
    @DisplayName("地址展示不探活：无凭据也给默认地址")
    void describeReturnsAddressWithoutProbing() {
        Map<String, Object> address = new MagpieUpstreamAdmin().describe(Map.of());

        assertTrue(String.valueOf(address.get("baseUrl")).contains("3425"));
        assertTrue(String.valueOf(address.get("pageUrl")).contains("3430"));
        // describe 只做归一化，不产生上游请求（真去请求会因本机没有网关而抛错）
        assertTrue(address.get("providers") == null);
    }
}
