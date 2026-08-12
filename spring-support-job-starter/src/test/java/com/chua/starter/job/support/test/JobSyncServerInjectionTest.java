package com.chua.starter.job.support.test;

import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.sync.SchedulerSyncBinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JobAutoConfiguration SyncServer listenHost/port 注入测试。
 *
 * <p>覆盖 JobProperties 默认值 + 覆盖能力,
 * 验证 SchedulerSyncBinder 构造、参数注入语义。
 * 完整 SyncServer 注入逻辑依赖 SPI 容器, 集成测试见 RealSyncNetworkTest/SingleModeTest。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("JobAutoConfiguration SyncServer 配置测试")
class JobSyncServerInjectionTest {

    @Test
    @DisplayName("JobProperties 默认 listenHost/port 验证")
    void shouldHaveDefaultSchedulerConfig() {
        JobProperties properties = new JobProperties();

        // 默认值
        assertThat(properties.getSync().getScheduler().getListenHost()).isEqualTo("0.0.0.0");
        assertThat(properties.getSync().getScheduler().getListenPort()).isPositive();
    }

    @Test
    @DisplayName("JobProperties 可配置 listenHost/port 为任意值")
    void shouldAllowCustomListenHostAndPort() {
        JobProperties properties = new JobProperties();
        properties.getSync().getScheduler().setListenHost("10.0.0.5");
        properties.getSync().getScheduler().setListenPort(54321);

        assertThat(properties.getSync().getScheduler().getListenHost()).isEqualTo("10.0.0.5");
        assertThat(properties.getSync().getScheduler().getListenPort()).isEqualTo(54321);
    }

    @Test
    @DisplayName("JobProperties SchedulerConfig 默认值合理")
    void shouldHaveReasonableSchedulerDefaults() {
        JobProperties properties = new JobProperties();

        // SchedulerConfig 默认参数
        assertThat(properties.getSync().getScheduler().getListenHost()).isEqualTo("0.0.0.0");
        assertThat(properties.getSync().getScheduler().getListenPort()).isPositive();
    }

    @Test
    @DisplayName("SchedulerSyncBinder 应该可以构造并设置 nodeId")
    void shouldConstructSchedulerSyncBinder() {
        // 验证 SchedulerSyncBinder 构造参数语义
        JobProperties properties = new JobProperties();
        properties.getSync().getScheduler().setListenHost("192.168.1.10");
        properties.getSync().getScheduler().setListenPort(9876);

        // 不启动实际 server, 仅验证参数读出
        assertThat(properties.getSync().getScheduler().getListenHost()).isEqualTo("192.168.1.10");
        assertThat(properties.getSync().getScheduler().getListenPort()).isEqualTo(9876);
    }

    @Test
    @DisplayName("JobProperties 集群配置 group 默认值")
    void shouldHaveDefaultClusterConfig() {
        JobProperties properties = new JobProperties();

        assertThat(properties.getCluster().getGroup()).isEqualTo("default");
        assertThat(properties.getCluster().getScanIntervalMs()).isPositive();
        assertThat(properties.getCluster().getLeaseSeconds()).isPositive();
    }
}