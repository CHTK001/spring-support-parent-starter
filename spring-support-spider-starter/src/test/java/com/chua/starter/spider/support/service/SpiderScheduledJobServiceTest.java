package com.chua.starter.spider.support.service;

import com.alibaba.fastjson2.JSON;
import com.chua.starter.spider.support.domain.SpiderExecutionPolicy;
import com.chua.starter.spider.support.domain.SpiderJobBinding;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderJobBindingRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.service.impl.JobStarterClient;
import com.chua.starter.spider.support.service.impl.SpiderScheduledJobServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SpiderScheduledJobService 单元测试（Mock job-starter）。
 */
@ExtendWith(MockitoExtension.class)
class SpiderScheduledJobServiceTest {

    @Mock
    private SpiderTaskRepository taskRepository;
    @Mock
    private SpiderJobBindingRepository jobBindingRepository;
    @Mock
    private JobStarterClient jobStarterClient;

    private SpiderScheduledJobService scheduledJobService;

    @BeforeEach
    void setUp() {
        scheduledJobService = new SpiderScheduledJobServiceImpl(
                taskRepository, jobBindingRepository, jobStarterClient);
    }

    // ── registerJob: 成功后 jobBindingId 被持久化 ─────────────────────────────

    @Test
    void registerJob_shouldPersistJobBindingId_whenRegistrationSucceeds() {
        Long taskId = 1L;
        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .cron("0 0 * * * ?")
                .jobChannel("default")
                .build();
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .id(taskId)
                .taskName("测试定时任务")
                .executionType(SpiderExecutionType.SCHEDULED)
                .executionPolicy(JSON.toJSONString(policy))
                .status(SpiderTaskStatus.READY)
                .build();

        when(taskRepository.getById(taskId)).thenReturn(task);
        when(jobStarterClient.registerJob(anyString(), eq("0 0 * * * ?"), eq("default"), eq("1"), anyString()))
                .thenReturn("job-binding-123");
        when(jobBindingRepository.save(any(SpiderJobBinding.class))).thenReturn(true);

        scheduledJobService.registerJob(taskId);

        ArgumentCaptor<SpiderJobBinding> bindingCaptor = ArgumentCaptor.forClass(SpiderJobBinding.class);
        verify(jobBindingRepository).save(bindingCaptor.capture());
        SpiderJobBinding savedBinding = bindingCaptor.getValue();
        assertThat(savedBinding.getJobBindingId()).isEqualTo("job-binding-123");
        assertThat(savedBinding.getTaskId()).isEqualTo(taskId);
        assertThat(savedBinding.getActive()).isTrue();
    }

    @Test
    void registerJob_shouldThrow_whenTaskNotFound() {
        when(taskRepository.getById(99L)).thenReturn(null);

        assertThatThrownBy(() -> scheduledJobService.registerJob(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void registerJob_shouldThrow_whenCronIsEmpty() {
        Long taskId = 2L;
        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .cron("")
                .build();
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .id(taskId)
                .taskName("无 cron 任务")
                .executionPolicy(JSON.toJSONString(policy))
                .status(SpiderTaskStatus.READY)
                .build();

        when(taskRepository.getById(taskId)).thenReturn(task);

        assertThatThrownBy(() -> scheduledJobService.registerJob(taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cron");
    }

    // ── registerJob: job-starter 失败时任务状态回滚 ───────────────────────────

    @Test
    void registerJob_shouldRollbackTaskStatus_whenJobStarterFails() {
        Long taskId = 3L;
        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .cron("0 0 * * * ?")
                .build();
        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .id(taskId)
                .taskName("失败任务")
                .executionPolicy(JSON.toJSONString(policy))
                .status(SpiderTaskStatus.RUNNING)
                .build();

        when(taskRepository.getById(taskId)).thenReturn(task);
        when(jobStarterClient.registerJob(anyString(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("job-starter 连接失败"));
        when(taskRepository.updateById(any())).thenReturn(true);

        assertThatThrownBy(() -> scheduledJobService.registerJob(taskId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("注册调度任务失败");

        // 验证状态被回滚
        ArgumentCaptor<SpiderTaskDefinition> taskCaptor = ArgumentCaptor.forClass(SpiderTaskDefinition.class);
        verify(taskRepository).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(SpiderTaskStatus.READY);
    }

    // ── pauseJob / resumeJob ──────────────────────────────────────────────────

    @Test
    void pauseJob_shouldCallJobStarterPause() {
        Long taskId = 4L;
        SpiderJobBinding binding = SpiderJobBinding.builder()
                .taskId(taskId).jobBindingId("job-456").active(true).build();

        when(jobBindingRepository.findActiveByTaskId(taskId)).thenReturn(Optional.of(binding));
        doNothing().when(jobStarterClient).pauseJob("job-456");

        scheduledJobService.pauseJob(taskId);

        verify(jobStarterClient).pauseJob("job-456");
    }

    @Test
    void resumeJob_shouldCallJobStarterResume() {
        Long taskId = 5L;
        SpiderJobBinding binding = SpiderJobBinding.builder()
                .taskId(taskId).jobBindingId("job-789").active(true).build();

        when(jobBindingRepository.findActiveByTaskId(taskId)).thenReturn(Optional.of(binding));
        doNothing().when(jobStarterClient).resumeJob("job-789");

        scheduledJobService.resumeJob(taskId);

        verify(jobStarterClient).resumeJob("job-789");
    }

    @Test
    void pauseJob_shouldThrow_whenNoBindingFound() {
        when(jobBindingRepository.findActiveByTaskId(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledJobService.pauseJob(6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未找到有效的调度绑定");
    }

    // ── deleteJob ─────────────────────────────────────────────────────────────

    @Test
    void deleteJob_shouldDeleteFromJobStarterAndClearBinding() {
        Long taskId = 7L;
        SpiderJobBinding binding = SpiderJobBinding.builder()
                .taskId(taskId).jobBindingId("job-del-1").active(true).build();

        when(jobBindingRepository.findActiveByTaskId(taskId)).thenReturn(Optional.of(binding));
        doNothing().when(jobStarterClient).deleteJob("job-del-1");
        doNothing().when(jobBindingRepository).deleteByTaskId(taskId);

        scheduledJobService.deleteJob(taskId);

        verify(jobStarterClient).deleteJob("job-del-1");
        verify(jobBindingRepository).deleteByTaskId(taskId);
    }

    @Test
    void deleteJob_shouldStillClearBinding_whenJobStarterFails() {
        Long taskId = 8L;
        SpiderJobBinding binding = SpiderJobBinding.builder()
                .taskId(taskId).jobBindingId("job-del-2").active(true).build();

        when(jobBindingRepository.findActiveByTaskId(taskId)).thenReturn(Optional.of(binding));
        doThrow(new RuntimeException("job-starter 不可用")).when(jobStarterClient).deleteJob("job-del-2");
        doNothing().when(jobBindingRepository).deleteByTaskId(taskId);

        // 不应抛出异常
        assertThatCode(() -> scheduledJobService.deleteJob(taskId)).doesNotThrowAnyException();
        verify(jobBindingRepository).deleteByTaskId(taskId);
    }
}
