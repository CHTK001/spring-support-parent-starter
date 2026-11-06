package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

import java.util.Map;

/**
 * 等待节点：控制采集节奏。
 *
 * <p>抓取节点本身不带限速，串行流程里在两个抓取节点之间插入本节点即可降低对目标站点的压力；
 * 填写随机上限后每次等待落在 [下限, 上限] 区间内，避免固定间隔被识别为机器行为。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CollectSleepExecutor implements CollectShapeExecutor {

    /**
     * 等待下限参数名。
     */
    public static final String MILLIS = "millis";

    /**
     * 等待上限参数名，大于下限时随机取值。
     */
    public static final String MAX_MILLIS = "maxMillis";

    @Override
    public String supportShape() {
        return "sleep";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "等待", "控制", "clock",
                "按毫秒等待，用于限速");
        return CollectShapeMetas.with(meta,
                CollectParamMeta.of(MILLIS, "等待毫秒", "number"),
                CollectParamMeta.of(MAX_MILLIS, "随机上限", "number"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        long millis = Math.max(0L, node.longParam(MILLIS, 0L));
        long max = node.longParam(MAX_MILLIS, 0L);
        if (max > millis) {
            millis = ThreadLocalRandom.current().nextLong(millis, max + 1);
        }
        if (millis == 0L) {
            return;
        }
        if (!context.isRunning()) {
            return;
        }
        Thread.sleep(millis);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        return "等待 " + node.param(MILLIS) + (node.param(MAX_MILLIS).isEmpty() ? "ms" : "~" + node.param(MAX_MILLIS) + "ms");
    }
}
