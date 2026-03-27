package com.chua.starter.job.support.remote;

import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.DefaultJobLog;
import com.chua.starter.job.support.log.JobFileAppender;
import com.chua.starter.job.support.thread.JobContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteJobExecutorDispatchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteRemoteRequestWithJobNoAndLogNo() throws Exception {
        JobFileAppender.initLogPath(tempDir.toString());
        String handlerName = "remote-handler-" + UUID.randomUUID().toString().replace("-", "");
        JobHandlerFactory.getInstance().register(handlerName, () -> {
            DefaultJobLog.log("remote handler executed: " + JobContext.getJobParam());
            JobContext.getJobContext().setSuccess("remote ok");
        });

        RemoteJobTriggerRequest request = new RemoteJobTriggerRequest();
        request.setJobNo("JOB202603250301");
        request.setJobName("remote-dispatch-test");
        request.setExecutorHandler(handlerName);
        request.setExecutorParams("payload");
        request.setLogId(555001L);
        request.setLogNo("JOBLOG202603250301");
        request.setLogDateTime(1_700_000_000_000L);
        request.setRemoteExecutorAddress("http://127.0.0.1:18083/payment/api");

        RemoteJobTriggerResponse response = new RemoteJobExecutorDispatchService().dispatch(request);

        assertThat(response.isAccepted()).isTrue();
        assertThat(response.getJobNo()).isEqualTo(request.getJobNo());
        assertThat(response.getLogNo()).isEqualTo(request.getLogNo());
        assertThat(response.getExecuteCode()).isEqualTo("SUCCESS");

        String logFileName = JobFileAppender.makeLogFileName(new Date(request.getLogDateTime()), request.getJobNo(), request.getLogNo());
        String logContent = Files.readString(Path.of(logFileName), StandardCharsets.UTF_8);
        assertThat(logContent).contains("remote handler executed: payload").contains("远程任务执行完成");
    }
}
