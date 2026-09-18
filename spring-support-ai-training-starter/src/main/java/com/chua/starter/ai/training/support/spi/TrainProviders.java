package com.chua.starter.ai.training.support.spi;

import com.chua.common.support.spi.ServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
* 训练引擎 SPI 注册表：按 类型 查找已接入的 {@link TrainProvider}。
*
* <p>实现通过 {@code META-INF/services/com...TrainProvider} 注册（标准 Java SPI），
* 与仓库既有 服务提供者 用法一致（向量 / datasearch 各 提供者）。</p>
*
* @author CH
* @since 4.0.0.42
 */
public final class TrainProviders {

    /**
    * 默认训练类型：Rnet50 迁移学习（既有语义，类型 缺省时的兼容值）。
    */
    public static final String DEFAULT_TYPE = "resnet50";

    /**
    * train提供者。
    */
    private TrainProviders() {
    }

    /**
    * 按类型查找训练引擎，未接入则抛出明确异常。
    *
    * @param type 训练类型（resnet50 / yolo ...）
    * @return 匹配的引擎实现
    * @throws IllegalArgumentException 未接入该类型时抛出（附带已接入清单便于排查）
    */
    public static TrainProvider require(String type) {
        Objects.requireNonNull(type, "type 不能为空");
        List<TrainProvider> providers = new ArrayList<>(ServiceProvider.of(TrainProvider.class).collect());
        TrainProvider hit = providers.stream()
                .filter(p -> type.equals(p.type()))
                .findFirst()
                .orElse(null);
        if (hit != null) {
            return hit;
        }
        String available = providers.stream()
                .map(TrainProvider::type)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("未接入 type=" + type + " 的训练引擎"
                + (available.isEmpty() ? "（当前无任何 TrainProvider 实现）" : "（已接入: " + available + "）"));
    }
}
