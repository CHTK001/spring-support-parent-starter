package com.chua.starter.strategy.template;

import lombok.RequiredArgsConstructor;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 默认策略链实现
 *
 * @author CH
 * @since 2025-12-25
 */
@RequiredArgsConstructor
public class DefaultStrategyChain implements StrategyChain {

    private final StrategyTemplate strategyTemplate;
    private final String[] strategies;

    @Override
    public <T> T execute(Supplier<T> supplier) {
        return execute(supplier, e -> {
            throw new RuntimeException("Strategy chain execution failed", e);
        });
    }

    @Override
    public <T> T execute(Supplier<T> supplier, Function<Throwable, T> fallback) {
        Supplier<T> wrapped = supplier;

        // 从后往前包装，实现链式调用
        for (int i = strategies.length - 1; i >= 0; i--) {
            String strategyName = strategies[i];
            StrategyInfo info = strategyTemplate.getStrategy(strategyName);
            if (info == null || !info.isEnabled()) {
                continue;
            }

            final Supplier<T> current = wrapped;
            wrapped = switch (info.getType()) {
                case RATE_LIMITER -> () -> strategyTemplate.executeWithRateLimit(strategyName, current);
                case CIRCUIT_BREAKER -> () -> strategyTemplate.executeWithCircuitBreaker(strategyName, current, fallback);
                case RETRY -> () -> strategyTemplate.executeWithRetry(strategyName, current);
                default -> current;
            };
        }

        try {
            return wrapped.get();
        } catch (Exception e) {
            return fallback.apply(e);
        }
    }

    @Override
    public void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    @Override
    public String[] getStrategies() {
        return strategies;
    }
}
