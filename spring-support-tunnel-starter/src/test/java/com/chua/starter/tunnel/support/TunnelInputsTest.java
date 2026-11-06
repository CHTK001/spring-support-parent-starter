// [P3C A20 豁免] A20/A30/A31: 测试内用于断言错误信息的映射值，已含备注。
package com.chua.starter.tunnel.support;

import com.chua.starter.common.support.tunnel.TunnelRecord;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 页面输入校验工具 {@link TunnelInputs} 的纯单元测试。
 *
 * <p>不涉及 Spring 容器与厂商 CLI，只覆盖「端口范围 / 模式 / 名称兜底
 * / 空串转 null / 公共字段归一」这些 REST 层之前必须过的一关。</p>
 * @author CH
 * @since 4.0.0.51
 */
class TunnelInputsTest {

    /**
     * 按端口 + 默认模式的合法请求体。
     */
    private TunnelRecord input(int port, String mode) {
        TunnelRecord r = new TunnelRecord();
        r.setLocalPort(port);
        r.setMode(mode);
        return r;
    }

    @Test
    void shouldRejectNullInput() {
        assertThatThrownBy(() -> TunnelInputs.requirePort(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("映射配置不能为空");
    }

    @Test
    void shouldRejectPortOutOfRange() {
        assertThatThrownBy(() -> TunnelInputs.requirePort(input(0, "http")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本地端口必须在 1 - 65535 之间");

        assertThatThrownBy(() -> TunnelInputs.requirePort(input(65536, "http")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本地端口必须在 1 - 65535 之间");

        // 边界值正常通过。
        assertThat(TunnelInputs.requirePort(input(1, "http"))).isEqualTo(1);
        assertThat(TunnelInputs.requirePort(input(65535, "http"))).isEqualTo(65535);
        assertThat(TunnelInputs.requirePort(input(8080, "http"))).isEqualTo(8080);
    }

    @Test
    void shouldRejectUnsupportedMode() {
        assertThatThrownBy(() -> TunnelInputs.requireMode("udp", List.of("http", "tcp"), "ngrok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 ngrok 模式: udp，可选: [http, tcp]");

        assertThat(TunnelInputs.requireMode("http", List.of("http", "tcp"), "ngrok")).isEqualTo("http");
        assertThat(TunnelInputs.requireMode("tcp", List.of("http", "tcp"), "ngrok")).isEqualTo("tcp");
    }

    @Test
    void shouldApplyCommonFields() {
        TunnelRecord input = input(8080, "http");
        input.setName("my-name");
        input.setRemark("  remark ");
        input.setEnabled(false);

        TunnelRecord target = new TunnelRecord();
        TunnelInputs.applyCommon(target, input, "ngrok");

        assertThat(target.getLocalPort()).isEqualTo(8080);
        assertThat(target.getName()).isEqualTo("my-name");
        assertThat(target.getRemark()).isEqualTo("remark");
        assertThat(target.isEnabled()).isFalse();
        assertThat(target.isAutoStart()).isFalse();
        assertThat(target.getProvider()).isEqualTo("ngrok");
    }

    @Test
    void shouldFallBackNameWhenEmpty() {
        TunnelRecord target = new TunnelRecord();
        TunnelInputs.applyCommon(target, input(8080, "http"), "cloudflared");
        assertThat(target.getName()).isEqualTo("cloudflared-8080");
        assertThat(target.getRemark()).isNull();
    }

    @Test
    void shouldTrimToNullAndNormalize() {
        assertThat(TunnelInputs.trimToNull(null)).isNull();
        assertThat(TunnelInputs.trimToNull("")).isNull();
        assertThat(TunnelInputs.trimToNull("  ")).isNull();
        assertThat(TunnelInputs.trimToNull("  a  ")).isEqualTo("a");

        assertThat(TunnelInputs.normalize(null, "fallback")).isEqualTo("fallback");
        assertThat(TunnelInputs.normalize("", "fallback")).isEqualTo("fallback");
        assertThat(TunnelInputs.normalize("  ", "fallback")).isEqualTo("fallback");
        assertThat(TunnelInputs.normalize(" value ", "fallback")).isEqualTo("value");
    }
}
