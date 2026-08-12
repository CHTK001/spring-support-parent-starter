# Quick Start — 5 分钟跑通

> 从零到第一个任务派发完成的最短路径。

---

## 1️⃣ 加依赖（30 秒）

`pom.xml`：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-taskdistribution-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

`${revision}` 用你工程的版本变量，或直接填 `4.0.0.42`。

---

## 2️⃣ 加 YAML 配置（30 秒）

`application.yml`：

```yaml
taskdistribution:
  enabled: true
  consumer-threads: 2
  batch-size: 1
  # store-type 默认 memory，无需配置
```

零配置即可跑通——内存存储、单消费线程。

---

## 3️⃣ 实现 1 个 TaskExecutor（1 分钟）

```java
package com.example.app.executor;

import com.chua.common.support.taskdistribution.spi.TaskExecutor;
import com.chua.common.support.taskdistribution.task.Task;
import com.chua.common.support.taskdistribution.task.TaskResult;
import org.springframework.stereotype.Component;

@Component
public class HelloExecutor implements TaskExecutor<String> {

    @Override
    public String taskType() {
        return "hello";
    }

    @Override
    public TaskResult<String> execute(Task<String> task) {
        String input = task.getPayload();
        return TaskResult.success(task.getTaskId(), "Hello, " + input);
    }
}
```

`@Component` 让 Spring 自动扫描 + starter 自动注册到 `TaskExecutorRegistry`。

---

## 4️⃣ 启动时跑一次（1 分钟）

```java
package com.example.app;

import com.chua.common.support.taskdistribution.manager.TaskManager;
import com.chua.common.support.taskdistribution.task.Task;
import com.chua.common.support.taskdistribution.task.TaskCallback;
import com.chua.common.support.taskdistribution.task.TaskResult;
import com.chua.common.support.taskdistribution.task.TaskPriority;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoRunner {

    @Bean
    public ApplicationRunner demoTask(TaskManager taskManager) {
        return args -> {
            Task<String> task = Task.<String>builder()
                .taskType("hello")
                .payload("CH")
                .priority(TaskPriority.HIGH)
                .timeoutMs(10_000)
                .maxRetries(2)
                .build();

            taskManager.addTask(task, new TaskCallback() {
                @Override
                public void onResult(TaskResult<?> result) {
                    System.out.println("✅ 任务成功: " + result.getData());
                }

                @Override
                public void onError(String taskId, String error) {
                    System.err.println("❌ 任务失败: " + taskId + " - " + error);
                }

                @Override
                public void onTimeout(String taskId) {
                    System.err.println("⏰ 任务超时: " + taskId);
                }
            });
        };
    }
}
```

---

## 5️⃣ 运行并观察日志（1 分钟）

```bash
mvn spring-boot:run
```

预期日志：

```
INFO  TaskDistributionAutoConfiguration : 任务分发初始化完成, nodeId: node-1731398400, strategy: ROUND, batchSize: 1, consumerThreads: 2
DEBUG TaskDistributionAutoConfiguration : 注册任务执行器: hello
DEBUG TaskManager : 任务添加: {nodeId}-0
DEBUG HelloExecutor : execute task ...
✅ 任务成功: Hello, CH
```

---

## 下一步

- **持久化**：切换 `store-type: db` + 建表（见 [README §数据库模式](../README.md#数据库模式)）
- **集群**：启用 `taskdistribution.server.*` 加入散射-汇集群
- **指标**：接入 Micrometer / Prometheus（计划中）
- **MDC 追踪**：见 [README §MDC 链路追踪](../README.md#mdc-链路追踪)
- **完整 API**：见 [README §管理接口](../README.md#管理接口)