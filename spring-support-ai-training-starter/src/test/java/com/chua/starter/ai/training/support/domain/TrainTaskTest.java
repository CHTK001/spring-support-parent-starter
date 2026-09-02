package com.chua.starter.ai.training.support.domain;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.spi.TrainProviders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TrainTask} 领域状态机与 type 路由键语义测试。
 *
 * <p>覆盖本次"type 请求入口可选"修复引入的领域行为：创建默认 resnet50、
 * {@link TrainTask#selectType(String)} 的 null/空串回退、仅 PENDING 可指定，
 * 以及启动/合并超参数等既有领域约束。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class TrainTaskTest {

    @Test
    @DisplayName("创建任务默认类型 = resnet50")
    void createdTaskDefaultsToResnet50() {
        TrainTask task = TrainTask.create("cats.zip", "/data/cats");

        assertThat(task.getType()).isEqualTo(TrainProviders.DEFAULT_TYPE);
        assertThat(task.getType()).isEqualTo("resnet50");
        assertThat(task.getStatus()).isEqualTo(TrainStatus.PENDING);
    }

    @Test
    @DisplayName("selectType(yolo) 指定后生效")
    void selectTypeSetsYolo() {
        TrainTask task = TrainTask.create("cars.zip", "/data/cars");

        String applied = task.selectType("yolo");

        assertThat(applied).isEqualTo("yolo");
        assertThat(task.getType()).isEqualTo("yolo");
    }

    @Test
    @DisplayName("selectType(null) 回退默认 resnet50")
    void selectTypeNullFallsBackToDefault() {
        TrainTask task = TrainTask.create("cats.zip", "/data/cats");

        String applied = task.selectType(null);

        assertThat(applied).isEqualTo("resnet50");
        assertThat(task.getType()).isEqualTo("resnet50");
    }

    @Test
    @DisplayName("selectType(空白串) 回退默认 resnet50")
    void selectTypeBlankFallsBackToDefault() {
        TrainTask task = TrainTask.create("cats.zip", "/data/cats");

        String applied = task.selectType("   ");

        assertThat(applied).isEqualTo("resnet50");
        assertThat(task.getType()).isEqualTo("resnet50");
    }

    @Test
    @DisplayName("启动(RUNNING)后不可再改 type")
    void selectTypeAfterStartThrows() {
        TrainTask task = TrainTask.create("cats.zip", "/data/cats");
        task.start();

        assertThatThrownBy(() -> task.selectType("yolo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING")
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("任务成功后不可再改 type")
    void selectTypeAfterSuccessThrows() {
        TrainTask task = TrainTask.create("cats.zip", "/data/cats");
        task.start();
        task.succeed(new TrainResult().setSuccess(true));

        assertThatThrownBy(() -> task.selectType("yolo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("mergeArgument 仅覆盖非空字段，保留 type 与既有默认值")
    void mergeArgumentKeepsTypeAndNonNullOnly() {
        TrainTask task = TrainTask.create("cats.zip", "/data/cats");
        task.selectType("yolo");

        // 仅携带 epoch 的部分更新
        TrainArgument incoming = TrainArgument.builder().epoch(25).build();
        task.mergeArgument(incoming);

        TrainArgument merged = task.getArgument();
        assertThat(merged.getEpoch()).isEqualTo(25);
        assertThat(merged.getBatchSize()).isEqualTo(2); // 默认值保留
        assertThat(task.getType()).isEqualTo("yolo");   // type 不受超参数合并影响
    }
}
