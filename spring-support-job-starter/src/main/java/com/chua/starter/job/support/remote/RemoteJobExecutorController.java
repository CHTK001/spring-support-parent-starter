package com.chua.starter.job.support.remote;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.job.support.JobProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 远程执行器入口。
 */
@RestController
public class RemoteJobExecutorController {

    private static final String ACCESS_TOKEN_HEADER = "X-Job-Access-Token";

    private final JobProperties jobProperties;
    private final RemoteJobExecutorDispatchService dispatchService;

    public RemoteJobExecutorController(JobProperties jobProperties,
                                       RemoteJobExecutorDispatchService dispatchService) {
        this.jobProperties = jobProperties;
        this.dispatchService = dispatchService;
    }

    @PostMapping("${plugin.job.remote-executor.dispatch-path:/v1/job-executor/dispatch}")
    public ReturnResult<RemoteJobTriggerResponse> dispatch(
            @RequestBody RemoteJobTriggerRequest request,
            @RequestHeader(value = ACCESS_TOKEN_HEADER, required = false) String accessToken) {
        validateAccessToken(accessToken);
        return ReturnResult.ok(dispatchService.dispatch(request));
    }

    private void validateAccessToken(String accessToken) {
        String expected = jobProperties.getRemoteExecutor() == null
                ? null
                : jobProperties.getRemoteExecutor().getAccessToken();
        if (!StringUtils.hasText(expected)) {
            return;
        }
        if (!StringUtils.hasText(accessToken) || !expected.trim().equals(accessToken.trim())) {
            throw new IllegalArgumentException("远程执行器访问令牌无效");
        }
    }
}
