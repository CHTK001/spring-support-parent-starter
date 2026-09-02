package com.chua.starter.ai.training.support.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TrainProviders} SPI 路由分发测试。
 *
 * <p>验证引擎注册表按 type 正确命中实现：resnet50 → DL4J、yolo → python-http；
 * 未接入 type / null 必须抛出明确异常（附带已接入清单）。纯 SPI 逻辑，不起 Spring 上下文，
 * 依赖 classpath 上 {@code META-INF/services/...TrainProvider} 的注册（与运行期一致）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class TrainProvidersTest {

    @Test
    @DisplayName("默认类型常量 = resnet50")
    void defaultTypeIsResnet50() {
        assertThat(TrainProviders.DEFAULT_TYPE).isEqualTo("resnet50");
    }

    @Test
    @DisplayName("require(resnet50) 命中 DL4J 引擎")
    void requireResnet50ReturnsDl4jProvider() {
        TrainProvider provider = TrainProviders.require("resnet50");

        assertThat(provider).isNotNull();
        assertThat(provider.type()).isEqualTo("resnet50");
        assertThat(provider.engine()).isEqualTo("dl4j");
    }

    @Test
    @DisplayName("require(yolo) 命中 python-http 引擎")
    void requireYoloReturnsPythonHttpProvider() {
        TrainProvider provider = TrainProviders.require("yolo");

        assertThat(provider).isNotNull();
        assertThat(provider.type()).isEqualTo("yolo");
        assertThat(provider.engine()).isEqualTo("python-http");
    }

    @Test
    @DisplayName("resnet50 与 yolo 是不同实现")
    void resnet50AndYoloAreDistinctProviders() {
        TrainProvider resnet = TrainProviders.require("resnet50");
        TrainProvider yolo = TrainProviders.require("yolo");

        assertThat(resnet).isNotSameAs(yolo);
        assertThat(resnet.getClass()).isNotEqualTo(yolo.getClass());
    }

    @Test
    @DisplayName("未知 type 抛出明确异常并附带已接入清单")
    void unknownTypeThrowsWithAvailableList() {
        assertThatThrownBy(() -> TrainProviders.require("yolov5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type=yolov5")
                .hasMessageContaining("resnet50")
                .hasMessageContaining("yolo");
    }

    @Test
    @DisplayName("null type 抛出异常（require 非空校验）")
    void nullTypeThrows() {
        assertThatThrownBy(() -> TrainProviders.require(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }
}
