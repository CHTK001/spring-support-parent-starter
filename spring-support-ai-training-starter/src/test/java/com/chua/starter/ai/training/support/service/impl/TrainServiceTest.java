package com.chua.starter.ai.training.support.service.impl;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.config.TrainProperties;
import com.chua.starter.ai.training.support.domain.TrainTask;
import com.chua.starter.ai.training.support.service.TrainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TrainServiceImpl} 服务层单元测试（直接构造，无需 Spring 容器）。
 *
 * <p>聚焦引擎分发失败时的任务状态语义：type 未接入（SPI 校验失败）必须在
 * 状态流转 <b>之前</b>抛错，任务保持 PENDING 供修正 type 后重试——回归
 * 「先 start() 后 require() 导致任务被污染成 RUNNING 僵尸」的历史缺陷。
 * 另覆盖超参数修改的状态门禁（仅 PENDING 可修改，RUNNING/终态均拒绝）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class TrainServiceTest {

    /** 组装服务：默认配置 + 空事件发布器（监听器无关断言）。 */
    private TrainService newService() {
        ApplicationEventPublisher noopPublisher = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(Object event) {
                // 生命周期事件与本次断言无关，忽略
            }

            @Override
            public void publishEvent(ApplicationEvent event) {
                // 兼容老签名（若为 default 方法则此覆盖无害）
            }
        };
        return new TrainServiceImpl(new TrainProperties(), noopPublisher);
    }

    @Test
    @DisplayName("未接入的 type 在引擎分发即抛错，任务保持 PENDING 可重试")
    void startTrainingWithUnknownTypeThrowsAndKeepsTaskPending() {
        TrainService service = newService();
        TrainTask task = service.createTask("demo.zip", "/tmp/demo-data");

        assertThatThrownBy(() -> service.startTraining(task.getId(), "no-such-engine"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-engine")
                .hasMessageContaining("未接入");

        // 核心语义：引擎校验失败不得污染任务状态（历史缺陷是先 start() 转 RUNNING）
        assertThat(task.getStatus())
                .as("引擎分发失败后任务应保持 PENDING，而非 RUNNING 僵尸")
                .isEqualTo(TrainStatus.PENDING);
        // type 已被记录，用户可直接以正确 type 再次 startTraining（仍 PENDING 可改）
        assertThat(task.getType()).isEqualTo("no-such-engine");
    }

    @Test
    @DisplayName("超参数仅 PENDING 可修改：RUNNING/终态均拒绝且原值不变")
    void updateArgumentRejectedUnlessTaskPending() {
        TrainService service = newService();
        TrainTask task = service.createTask("demo.zip", "/tmp/demo-data");

        // PENDING → 允许部分更新
        service.updateArgument(task.getId(), TrainArgument.builder().epoch(20).build());
        assertThat(task.getArgument().getEpoch()).as("PENDING 状态应允许更新超参数").isEqualTo(20);

        // RUNNING → 拒绝（修改会污染进行中训练）
        task.start();
        assertThatThrownBy(() -> service.updateArgument(task.getId(), TrainArgument.builder().epoch(30).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING")
                .hasMessageContaining("PENDING");
        assertThat(task.getArgument().getEpoch()).as("RUNNING 拒绝后超参数保持原值").isEqualTo(20);

        // 终态（FAILED）→ 拒绝（历史缺陷：只拦 RUNNING，终态任务静默接受无意义的合并）
        task.fail("demo fail");
        assertThatThrownBy(() -> service.updateArgument(task.getId(), TrainArgument.builder().epoch(40).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED")
                .hasMessageContaining("PENDING");
        assertThat(task.getArgument().getEpoch()).as("终态拒绝后超参数保持原值").isEqualTo(20);

        // SUCCESS 终态同样拒绝
        TrainTask done = service.createTask("ok.zip", "/tmp/done-data");
        done.start();
        done.succeed(new TrainResult().setSuccess(true));
        assertThatThrownBy(() -> service.updateArgument(done.getId(), TrainArgument.builder().epoch(50).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCESS");
    }
}
