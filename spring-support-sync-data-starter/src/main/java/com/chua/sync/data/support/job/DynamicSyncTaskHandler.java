package com.chua.sync.data.support.job;

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

import java.util.HashMap;
import java.util.Map;

/**
 * 动态同步任务处理器
 * <p>
 * 支持通过 Groovy 脚本动态配置同步任务。
 * 在 monitor_job 表中配置 GLUE 脚本，实现动态的数据同步任务。
 * </p>
 *
 * <p>使用示例（Groovy脚本）:</p>
 * <pre>{@code
 * import com.chua.starter.sync.job.DynamicSyncTaskHandler
 *
 * class MySyncTask extends DynamicSyncTaskHandler {
 *     @Override
 *     protected SyncFlowConfig buildConfig() {
 *         return SyncFlowConfig.builder()
 *             .inputType("mysql")
 *             .inputConfig([
 *                 "url": "jdbc:mysql://localhost:3306/source",
 *                 "username": "root",
 *                 "password": "123456",
 *                 "table": "users"
 *             ])
 *             .outputType("elasticsearch")
 *             .outputConfig([
 *                 "hosts": "localhost:9200",
 *                 "index": "users"
 *             ])
 *             .batchSize(100)
 *             .build()
 *     }
 * }
 * }</pre>
 *
 * @author CH
 * @since 2024/12/19
 * @version 1.0.0
 */
@Slf4j
public abstract class DynamicSyncTaskHandler implements JobHandler {

    /**
     * 构建同步流配置
     * <p>子类需要实现此方法来定义同步任务的配置</p>
     *
     * @return 同步流配置
     */
    protected abstract SyncFlowConfig buildConfig();

    @Override
    public void execute() throws Exception {
        String param = JobContext.getJobParam();
        DefaultJobLog.log("开始执行动态同步任务, 参数: " + param);

        try {
            SyncFlowConfig config = buildConfig();
            if (config == null) {
                throw new IllegalArgumentException("同步配置不能为空");
            }

            if (StringUtils.isNotEmpty(param)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> runtimeParams = Json.fromJson(param, Map.class);
                    config.mergeRuntimeParams(runtimeParams);
                } catch (Exception e) {
                    log.warn("解析运行时参数失败: {}", param);
                }
            }

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
    private SyncFlow buildSyncFlow(SyncFlowConfig config) {
        SyncFlow.Builder builder = SyncFlow.builder("dynamic-sync-task");

        if (config.getInputType() != null) {
            Input input = createInput(config.getInputType(), config.getInputConfig());
            builder.addInput(input);
        }

        if (config.getOutputType() != null) {
            Output output = createOutput(config.getOutputType(), config.getOutputConfig());
            builder.addOutput(output);
        }

        if (config.getBatchSize() != null) {
            builder.batchSize(config.getBatchSize());
        }

        return builder.build();
    }

    private Input createInput(String type, Map<String, Object> config) {
        ServiceProvider<Input> provider = ServiceProvider.of(Input.class);
        return provider.getNewExtension(type, config != null ? config : new HashMap<>());
    }

    private Output createOutput(String type, Map<String, Object> config) {
        ServiceProvider<Output> provider = ServiceProvider.of(Output.class);
        return provider.getNewExtension(type, config != null ? config : new HashMap<>());
    }

    /**
     * 同步流配置
     */
    @lombok.Data
    @lombok.Builder
    public static class SyncFlowConfig {
        /**
         * 输入类型 (SPI名称)
         */
        private String inputType;
        /**
         * 输入配置
         */
        private Map<String, Object> inputConfig;
        /**
         * 输出类型 (SPI名称)
         */
        private String outputType;
        /**
         * 输出配置
         */
        private Map<String, Object> outputConfig;
        /**
         * 数据中心类型 (SPI名称)
         */
        private String dataCenterType;
        /**
         * 数据中心配置
         */
        private Map<String, Object> dataCenterConfig;
        /**
         * 过滤器配置 (type -> config)
         */
        private Map<String, Map<String, Object>> filters;
        /**
         * 批处理大小
         */
        private Integer batchSize;
        /**
         * 消费超时时间 (ms)
         */
        private Integer consumeTimeout;
        /**
         * 重试次数
         */
        private Integer retryCount;
        /**
         * 重试间隔 (ms)
         */
        private Integer retryInterval;

        /**
         * 合并运行时参数
         *
         * @param params 运行时参数
         */
        public void mergeRuntimeParams(Map<String, Object> params) {
            if (params == null) {
                return;
            }
            if (params.containsKey("batchSize")) {
                this.batchSize = ((Number) params.get("batchSize")).intValue();
            }
            if (params.containsKey("consumeTimeout")) {
                this.consumeTimeout = ((Number) params.get("consumeTimeout")).intValue();
            }
            if (params.containsKey("retryCount")) {
                this.retryCount = ((Number) params.get("retryCount")).intValue();
            }
            if (params.containsKey("retryInterval")) {
                this.retryInterval = ((Number) params.get("retryInterval")).intValue();
            }
        }
    }
}
