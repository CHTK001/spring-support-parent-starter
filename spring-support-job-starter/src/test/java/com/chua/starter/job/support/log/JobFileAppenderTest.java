package com.chua.starter.job.support.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JobFileAppenderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateNumberBasedLogFileAndReadContent() throws Exception {
        JobFileAppender.initLogPath(tempDir.toString());

        String logFileName = JobFileAppender.makeLogFileName(new Date(1_700_000_000_000L), "JOB202603250001", "JOBLOG202603250001");
        JobFileAppender.appendLog(logFileName, "first line");
        JobFileAppender.appendLog(logFileName, "second line");

        assertThat(logFileName).contains("JOB202603250001").contains("JOBLOG202603250001");
        assertThat(Files.exists(Path.of(logFileName))).isTrue();

        LogResult result = JobFileAppender.readLog(logFileName, 1);
        assertThat(result.getLogContent()).contains("first line").contains("second line");
        assertThat(result.getToLineNum()).isGreaterThanOrEqualTo(2);
    }
}
