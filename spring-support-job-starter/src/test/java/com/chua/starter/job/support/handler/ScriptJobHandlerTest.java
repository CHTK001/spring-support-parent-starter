package com.chua.starter.job.support.handler;

import com.chua.starter.job.support.GlueTypeEnum;
import com.chua.starter.job.support.log.JobFileAppender;
import com.chua.starter.job.support.thread.JobContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptJobHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecutePythonScriptAndWriteOutputToNumberBasedLogFile() throws Exception {
        JobFileAppender.initLogPath(tempDir.toString());

        String jobNo = "JOB202603250201";
        String jobLogNo = "JOBLOG202603250201";
        String logFileName = JobFileAppender.makeLogFileName(new Date(1_700_000_000_000L), jobNo, jobLogNo);
        JobContext.setJobContext(new JobContext(3003L, 4004L, jobNo, jobLogNo, "payload", logFileName, 2, 4));
        try {
            ScriptJobHandler handler = new ScriptJobHandler(
                    4004,
                    jobNo,
                    System.currentTimeMillis(),
                    "import sys\nprint('payload=' + sys.argv[1])\nprint('shard=' + sys.argv[2] + '/' + sys.argv[3])\n",
                    GlueTypeEnum.GLUE_PYTHON
            );
            handler.execute();
        } finally {
            JobContext.removeJobContext();
        }

        String logContent = Files.readString(Path.of(logFileName), StandardCharsets.UTF_8);
        assertThat(logContent).contains("payload=payload").contains("shard=2/4");
    }
}
