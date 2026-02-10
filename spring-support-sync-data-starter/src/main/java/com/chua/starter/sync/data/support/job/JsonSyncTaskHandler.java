package com.chua.starter.sync.data.support.job;

import com.chua.common.support.text.json.Json;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.sync.SyncFlow;
import com.chua.common.support.sync.Input;
import com.chua.common.support.sync.Output;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.log.DefaultJobLog;
import com.chua.starter.job.support.thread.JobContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON配置同步任务处理器
 * <p>
 * 支持通过 JSON 参数配置同步任务，无需编写 Groovy 脚本。
 * 在 monitor_job 表中配置：
 * - job_execute_bean: jsonSyncTaskHandler
 * - job_execute_param: JSON配置
 * </p>
 *
 * <p>JSON配置示例:</p>
 * <pre>{@code
 * {
 *   "inputType": "mysql",
 *   "inputConfig": {
 *     "url": "jdbc:mysql://localhost:3306/source",
 *     "username": "root",
 *     "password": "123456",
 *     "table": "users",
 *     "query": "SELECT * FROM users WHERE status = 1"
 *   },
 *   "outputType": "elasticsearch",
 *   "outputConfig": {
 *     "hosts": "localhost:9200",
 *     "index": "users"
 *   },
 *   "filters": {
 *     "field-mapper": {
 *       "mappings": {"user_name": "userName", "create_time": "createTime"}
 *     }
 *   },
 *   "batchSize": 100,
 *   "retryCount": 3
 * }
 * }</pre>
 *
 * @author CH
 * @since 2024/12/19
 * @version 1.0.0
 */
@Slf4j
@Component("jsonSyncTaskHandler")
public class JsonSyncTaskHandler implements JobHandler {

    @Override
    public void execute() throws Exception {
        String param = JobContext.getJobParam();
        if (StringUtils.isEmpty(param)) {
            throw new IllegalArgumentException("同步配置参数不能为空，请在 job_execute_param 中配置 JSON");
        }

        DefaultJobLog.log("开始解析同步配置...");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = Json.fromJson(param, Map.class);

            SyncFlow syncFlow = buildSyncFlow(config);

            DefaultJobLog.log("开始数据同步...");
            long startTime = System.currentTimeMillis();
            syncFlow.start();
            long cost = System.currentTimeMillis() - startTime;

            DefaultJobLog.log("同步完成, 耗时: " + cost + "ms");

        } catch (Exception e) {
            DefaultJobLog.log("同步任务执行失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 构建 SyncFlow
     */
    @SuppressWarnings("unchecked")
    private SyncFlow buildSyncFlow(Map<String, Object> config) {
        SyncFlow.Builder builder = SyncFlow.builder("json-sync-task");

        String inputType = (String) config.get("inputType");
        if (inputType != null) {
            Map<String, Object> inputConfig = (Map<String, Object>) config.getOrDefault("inputConfig", new HashMap<>());
            Input input = createInput(inputType, inputConfig);
            builder.addInput(input);
            DefaultJobLog.log("配置输入: " + inputType);
        } else {
            throw new IllegalArgumentException("必须配置 inputType");
        }

        String outputType = (String) config.get("outputType");
        if (outputType != null) {
            Map<String, Object> outputConfig = (Map<String, Object>) config.getOrDefault("outputConfig", new HashMap<>());
            Output output = createOutput(outputType, outputConfig);
            builder.addOutput(output);
            DefaultJobLog.log("配置输出: " + outputType);
        } else {
            throw new IllegalArgumentException("必须配置 outputType");
        }

        if (config.containsKey("batchSize")) {
            builder.batchSize(((Number) config.get("batchSize")).intValue());
        }

        return builder.build();
    }

    private Input createInput(String type, Map<String, Object> config) {
        ServiceProvider<Input> provider = ServiceProvider.of(Input.class);
        return provider.getNewExtension(type, config);
    }

    private Output createOutput(String type, Map<String, Object> config) {
        ServiceProvider<Output> provider = ServiceProvider.of(Output.class);
        return provider.getNewExtension(type, config);
    }
}
