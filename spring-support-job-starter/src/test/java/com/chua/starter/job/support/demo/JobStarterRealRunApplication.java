package com.chua.starter.job.support.demo;

import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.log.DefaultJobLog;
import com.chua.starter.job.support.service.JobDynamicConfigService;
import com.chua.starter.job.support.thread.JobContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication(scanBasePackages = {
        "com.chua.starter.job.support"
})
public class JobStarterRealRunApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobStarterRealRunApplication.class, args);
    }

    @Bean
    DemoJobState demoJobState() {
        return new DemoJobState();
    }

    @Getter
    public static class DemoJobState {
        private final AtomicInteger executeCount = new AtomicInteger();
        private volatile String lastParam;
        private volatile Instant lastExecutedAt;
        private volatile String lastThread;

        public void markExecuted(String param) {
            executeCount.incrementAndGet();
            lastParam = param;
            lastExecutedAt = Instant.now();
            lastThread = Thread.currentThread().getName();
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class DemoJobHandler {
        private final DemoJobState demoJobState;

        @Job("demoJob")
        public void execute() {
            String param = JobContext.getJobParam();
            demoJobState.markExecuted(param);
            DefaultJobLog.log("demoJob real run executed, param=" + param);
            JobContext.getJobContext().setSuccess("demoJob success: " + param);
        }
    }

    @RestController
    @RequiredArgsConstructor
    public static class DemoJobStatusController {
        private final DemoJobState demoJobState;
        private final JobDynamicConfigService jobDynamicConfigService;

        private static final String DEMO_JOB_NAME = "demo-local-job";
        private static final String DEMO_CRON = "0/3 * * * * ?";

        @GetMapping("/demo/job/status")
        public Map<String, Object> status() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("executeCount", demoJobState.getExecuteCount().get());
            result.put("lastParam", demoJobState.getLastParam());
            result.put("lastExecutedAt", demoJobState.getLastExecutedAt());
            result.put("lastThread", demoJobState.getLastThread());
            result.put("job", safeGetDemoJob());
            return result;
        }

        @PostMapping("/demo/job/setup")
        public Map<String, Object> setup(@RequestParam(defaultValue = DEMO_CRON) String cron,
                                         @RequestParam(defaultValue = "cron-param") String param,
                                         @RequestParam(defaultValue = "true") boolean autoStart) {
            Integer jobId = jobDynamicConfigService.registerOrUpdateJob(
                    DEMO_JOB_NAME,
                    cron,
                    "demoJob",
                    param,
                    "real run local demo",
                    autoStart
            );
            return jobResult(jobDynamicConfigService.getJobById(jobId));
        }

        @PostMapping("/demo/job/trigger")
        public Map<String, Object> trigger(@RequestParam(defaultValue = "manual-param") String param) {
            SysJob job = jobDynamicConfigService.getJobByName(DEMO_JOB_NAME);
            if (job == null || job.getJobId() == null) {
                throw new IllegalStateException("演示任务不存在，请先调用 /demo/job/setup");
            }
            boolean accepted = jobDynamicConfigService.triggerJob(job.getJobId(), param);
            Map<String, Object> result = jobResult(jobDynamicConfigService.getJobById(job.getJobId()));
            result.put("accepted", accepted);
            return result;
        }

        private Map<String, Object> jobResult(SysJob job) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("job", job);
            result.put("executeCount", demoJobState.getExecuteCount().get());
            result.put("lastParam", demoJobState.getLastParam());
            result.put("lastExecutedAt", demoJobState.getLastExecutedAt());
            return result;
        }

        private SysJob safeGetDemoJob() {
            try {
                return jobDynamicConfigService.getJobByName(DEMO_JOB_NAME);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
