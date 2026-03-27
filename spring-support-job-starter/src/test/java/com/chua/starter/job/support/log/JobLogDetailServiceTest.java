package com.chua.starter.job.support.log;

import com.chua.starter.job.support.entity.SysJobLogDetail;
import com.chua.starter.job.support.mapper.SysJobLogDetailMapper;
import com.chua.starter.job.support.thread.JobContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobLogDetailServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistStructuredDetailAndAppendReadableFile() throws Exception {
        JobFileAppender.initLogPath(tempDir.toString());
        SysJobLogDetailMapper mapper = mock(SysJobLogDetailMapper.class);
        when(mapper.insert(any(SysJobLogDetail.class))).thenReturn(1);

        JobLogDetailService service = new JobLogDetailService(mapper);
        String jobNo = "JOB202603250101";
        String jobLogNo = "JOBLOG202603250101";
        String logFileName = JobFileAppender.makeLogFileName(new Date(1_700_000_000_000L), jobNo, jobLogNo);

        JobContext jobContext = new JobContext(1001L, 2002L, jobNo, jobLogNo, "payload", logFileName, 0, 1);
        jobContext.getAttributes().put("handler", "payment-notify-cleanup");
        jobContext.getAttributes().put("profile", "job");
        jobContext.getAttributes().put("address", "http://127.0.0.1:18083/payment/api");

        SysJobLogDetail detail = service.log(jobContext, "INFO", "detail payload", "RUNNING", 60);

        ArgumentCaptor<SysJobLogDetail> detailCaptor = ArgumentCaptor.forClass(SysJobLogDetail.class);
        verify(mapper).insert(detailCaptor.capture());
        SysJobLogDetail persisted = detailCaptor.getValue();

        assertThat(detail.getJobNo()).isEqualTo(jobNo);
        assertThat(detail.getJobLogNo()).isEqualTo(jobLogNo);
        assertThat(persisted.getJobLogDetailHandler()).isEqualTo("payment-notify-cleanup");
        assertThat(persisted.getJobLogDetailProfile()).isEqualTo("job");
        assertThat(persisted.getJobLogDetailAddress()).isEqualTo("http://127.0.0.1:18083/payment/api");
        assertThat(persisted.getJobLogDetailFilePath()).isEqualTo(logFileName);

        String logContent = Files.readString(Path.of(logFileName), StandardCharsets.UTF_8);
        assertThat(logContent).contains("detail payload").contains("RUNNING").contains("60%");
    }
}
