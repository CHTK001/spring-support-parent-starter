# spring-support-ai-training-starter

AI 训练平台的 REST 接入模块：ZIP 训练数据上传与解压、异步训练调度、进度与超参数管理。
训练引擎不写死，按 `type` 经 Java SPI 分发——`resnet50` 走 DL4J 迁移学习，`yolo` 走本机 Python 子进程。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| 训练数据上传（ZIP 魔法数校验 + 文件名净化 + 解压） | `DataService` / `DataServiceImpl` | `POST /v2/train/upload`（`MultipartFile file`） |
| 训练任务创建与状态机（PENDING → RUNNING → SUCCESS / FAILED / CANCELLED） | `TrainTask` | `TrainTask.create(String originalFileName, String dataPath)` |
| 异步训练调度（守护线程 + 引擎回调接线） | `TrainServiceImpl` | `TrainService#startTraining(String taskId, String type)` |
| 训练引擎分发 | `TrainProvider` / `TrainProviders` | `TrainProviders#require(String type)` |
| DL4J 迁移学习引擎（`type=resnet50`，默认） | `Dl4jTrainProvider` | `ResNet50TransferTrainer#train(...)` |
| YOLO 引擎（`type=yolo`，Python 子进程 + HTTP 轮询） | `YoloTrainProvider` | `META-INF/services/com.chua.starter.ai.training.support.spi.TrainProvider` |
| 进度查询 / 任务列表 | `TrainTaskView`、`TrainTaskView.TrainProgressView` | `GET /v2/train/status`、`GET /v2/train/list` |
| 超参数部分更新（仅 PENDING 可改） | `TrainArgumentRequest`、`TrainTask#mergeArgument` | `POST /v2/train/argument` |
| 生命周期事件广播 | `TrainLifecycleEvent`、`TrainStage` | `ApplicationEventPublisher#publishEvent` |
| 服务信息登记 | `TrainAutoConfiguration#trainServiceInfoContributor` | `ServiceInfoContributor` |

自动配置入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` →
`com.chua.starter.ai.training.support.config.TrainAutoConfiguration`。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ai-training-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本 | 作用 |
| --- | --- | --- |
| `com.chua:utils-support-deeplearning-dl4j-starter` | `${utils.version}` = `4.0.0.50` | `TrainArgument` / `TrainProgress` / `TrainResult` / `TrainListener` / `ResNet50TransferTrainer` |
| `org.springframework.boot:spring-boot-starter-web` | 由父 POM 管理 | REST 端点与 `MultipartFile` |
| `org.springframework.boot:spring-boot-autoconfigure` | 由父 POM 管理 | `@AutoConfiguration` / `@ConditionalOnProperty` |
| `com.chua:spring-support-oauth-client-starter` | `4.0.0.51`（同父版本） | 提供 `@Permission` 鉴权注解（由它传递引入 `spring-support-common-starter`） |
| `io.swagger.core.v3:swagger-annotations` | 由父 POM 管理 | `@Tag` / `@Operation` / `@Schema` |
| `jakarta.servlet:jakarta.servlet-api` | 由父 POM 管理（`provided`） | 仅编译期需要 |
| `org.projectlombok:lombok` | 由父 POM 管理（`provided`） | 日志与样板代码 |
| `org.springframework.boot:spring-boot-configuration-processor` | 由父 POM 管理（`optional`） | 生成配置元数据 |

父 POM：`com.chua:spring-support-parent-starter:4.0.0.51`，`java.version = 25`。

## 三、配置项

前缀 `plugin.ai.training`，绑定类 `com.chua.starter.ai.training.support.config.TrainProperties`
（`@EnableConfigurationProperties(TrainProperties.class)`）。

| 配置 key | 默认值 | 说明 |
| --- | --- | --- |
| `plugin.ai.training.enabled` | `true` | 总开关。`false` 时 `TrainAutoConfiguration` 整体不装配（`matchIfMissing = true`） |
| `plugin.ai.training.max-size` | `3000` | 上传大小上限（MB）。当前仅用于服务信息展示，未拦截上传（见第七节） |
| `plugin.ai.training.model-path` | Windows：`C:\aias\file\resnet50_dl4j_inference.v3.zip`；其他平台：`~/.aias/file/resnet50_dl4j_inference.v3.zip` | 冷启动基座模型路径 |
| `plugin.ai.training.data-root-path` | Windows：`C:\aias\file\image_root\`；其他平台：`~/.aias/file/image_root/` | 数据解压根目录 |
| `plugin.ai.training.save-path` | Windows：`C:\aias\file\modelv2\`；其他平台：`~/.aias/file/modelv2/` | 新训练模型保存目录 |
| `plugin.ai.training.upload-path` | Windows：`C:\aias\file\`；其他平台：`~/.aias/file/` | 上传文件落盘目录 |

默认目录由 `TrainProperties#defaultBaseDir()` 按 `os.name` 推导，不是配置项。

此外 `YoloTrainProvider` 读取两个 **JVM 系统属性**（不是 `@ConfigurationProperties` 项，需用 `-D` 传入）：

| 系统属性 | 默认行为 | 说明 |
| --- | --- | --- |
| `plugin.ai.training.yolo.python` | Windows 用 `python`，其他平台用 `python3` | Python 可执行文件 |
| `plugin.ai.training.yolo.script` | 使用 classpath 上的 `python/yolo_train_service.py` | 外部训练脚本路径（文件存在才生效） |

```yaml
plugin:
  ai:
    training:
      enabled: true
      model-path: D:/aias/models/resnet50_dl4j_inference.v3.zip
      data-root-path: D:/aias/data/image_root/
      save-path: D:/aias/models/modelv2/
      upload-path: D:/aias/data/upload/
```

## 四、快速开始

**第一步：引入依赖**（见第二节 Maven 坐标）。宿主缺失 `spring-boot-starter-web` 时需自行补齐，本模块已声明。

**第二步：确认目录**。`upload-path` 在保存上传文件时会自动创建；`data-root-path` / `save-path` 需保证
进程可写。`model-path` 指向的预训练模型必须真实存在（`resnet50` 冷启动才会成功）。

**第三步：调用上传 → 启动 → 轮询**（端点见下一节）。三步之间用 `taskId` 串联。

## 五、运行示例

### 5.1 调用示例

REST 全流程（`@Permission("admin")`，需带宿主鉴权上下文）：

```text
POST /v2/train/upload        Content-Type: multipart/form-data    字段名: file（仅 ZIP）
POST /v2/train/trigger       Content-Type: application/json       {"taskId":"<id>","type":"resnet50"}
POST /v2/train/argument      Content-Type: application/json       {"taskId":"<id>","epoch":50,"batchSize":16}
GET  /v2/train/status?taskId=<id>
GET  /v2/train/list
```

响应包装为 `ReturnResult<T>`；上传的 `data` 是 `TrainUploadView(taskId, fileName, dataPath)`，
状态查询的 `data` 是 `TrainTaskView`（含 `status`、`progress`、`modelPath`、`classLabels`、`errorMessage` 等）。

Java 侧直接注入服务，按步骤调用（**本模块自身没有链式 API**：`TrainService` / `DataService` 都是普通方法；
超参数对象 `TrainArgument` 由上游 DL4J starter 提供，用 `TrainArgument.builder()...build()` 链式组装）：

```java
@Service
@RequiredArgsConstructor
public class MyTrainingJob {

    private final DataService dataService;      // 由 TrainAutoConfiguration 注册
    private final TrainService trainService;    // 由 TrainAutoConfiguration 注册

    public String submit(byte[] zipBytes, String fileName) throws Exception {
        // 1) 落盘 + 解压（内部校验 ZIP 魔数 PK\x03\x04）
        String savedPath = dataService.saveUploadedFile(zipBytes, fileName);
        String dataPath = dataService.unzip(savedPath);

        // 2) 建任务（PENDING），指定引擎类型（仅 PENDING 可指定）
        TrainTask task = trainService.createTask(fileName, dataPath);

        // 3) 覆盖超参数（部分更新语义：只覆盖非空字段）
        trainService.updateArgument(task.getId(), TrainArgument.builder()
                .epoch(50)
                .batchSize(16)
                .nClasses(5)
                .learningRate(0.001)
                .trainPercent(80)
                .build());

        // 4) 异步启动；type 为空/空白时回退 resnet50
        trainService.startTraining(task.getId(), "resnet50");
        return task.getId();
    }
}
```

`TrainTask` 的状态机由领域对象自身守卫，直接调用会得到明确异常：

```java
TrainTask task = trainService.getTask(taskId);
String type = task.selectType("yolo");   // 非 PENDING 抛 IllegalStateException
task.start();                            // 非 PENDING 抛 IllegalStateException
```

用 Spring 事件跟随训练过程（`TrainLifecycleEvent#getSource()` 即 taskId）：

```java
@Component
public class TrainWatcher {

    @EventListener(TrainLifecycleEvent.class)
    public void onTrain(TrainLifecycleEvent event) {
        switch (event.getStage()) {
            case TASK_CREATED, ARGUMENT_UPDATED -> { /* 准备阶段，progress 可为 null */ }
            case TRAIN_STARTED -> System.out.println("开始: " + event.getTaskId());
            case EPOCH_PROGRESS -> System.out.println("进度: " + event.getProgress().getProgress());
            case TRAIN_SUCCEEDED -> System.out.println("成功: " + event.getMessage());
            case TRAIN_FAILED, TRAIN_CANCELLED -> System.out.println("结束: " + event.getMessage());
        }
    }
}
```

`TrainStage` 全部取值：`TASK_CREATED`、`ARGUMENT_UPDATED`、`TRAIN_STARTED`、`EPOCH_PROGRESS`、
`TRAIN_SUCCEEDED`、`TRAIN_FAILED`、`TRAIN_CANCELLED`。

### 5.2 SPI 扩展示例

扩展点：`com.chua.starter.ai.training.support.spi.TrainProvider`，通过标准 Java SPI 登记，
由 `TrainProviders#require(String type)` 按 `type()` 匹配后交给 `TrainServiceImpl` 异步执行。

**第一步：实现接口**（三个方法都必须给真实值，`type()` 是前端传的 `type` 路由键）：

```java
public class MyOnnxTrainProvider implements TrainProvider {

    @Override
    public String type() {
        return "onnx-cls";           // 前端/接口传 type=onnx-cls 时命中
    }

    @Override
    public String engine() {
        return "onnxruntime";        // 仅用于日志与监控展示
    }

    @Override
    public TrainResult train(TrainArgument argument, TrainContext ctx, TrainListener listener) throws Exception {
        TrainProgress progress = new TrainProgress().setStatus(TrainStatus.RUNNING);
        listener.onStarted(progress);              // 可为 TrainListener.NOOP
        // ctx.modelPath() / ctx.savePath() / ctx.dataRootPath() 由平台解析好传入
        TrainResult result = new TrainResult().setSuccess(true);
        listener.onFinished(progress);
        return result;
    }
}
```

**第二步：在 SPI 文件登记**。新建
`src/main/resources/META-INF/services/com.chua.starter.ai.training.support.spi.TrainProvider`，
每行一个实现全限定名（本模块自带的文件内容即两行 `...impl.Dl4jTrainProvider`、`...impl.YoloTrainProvider`）：

```text
com.example.training.MyOnnxTrainProvider
```

**第三步：生效**。`POST /v2/train/trigger` 传 `{"taskId":"<id>","type":"onnx-cls"}` 即可；
若类型未登记，`TrainProviders#require` 抛 `IllegalArgumentException` 并列出已接入清单，此时任务仍保持 PENDING。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
| --- | --- | --- |
| `utils-support-deeplearning-dl4j-starter` | 本模块依赖它 | 提供 `TrainArgument` / `TrainProgress` / `TrainResult` / `TrainListener` 与 `ResNet50TransferTrainer` |
| `spring-support-oauth-client-starter` | 本模块依赖它 | `@Permission("admin")` 的注解来源（传递带入 `spring-support-common-starter`） |
| `spring-support-common-starter` | 传递依赖 | `ServiceInfoContributor`、`com.chua.starter.common.support.annotations.Permission` |
| `utils-support-common-starter` | 传递依赖 | `com.chua.common.support.lang.code.ReturnResult` |
| `spring-support-socket-starter` / `spring-support-*` 其它 | 无直接关系 | 训练进度可通过 `TrainLifecycleEvent` 自行桥接，模块内不依赖任何推送通道 |

当前父 POM 中没有其它模块声明依赖 `spring-support-ai-training-starter`（以代码为准）。

## 七、已知限制

1. **任务只存在内存**。`TrainServiceImpl` 用 `ConcurrentHashMap<String, TrainTask>` 保存任务，
   进程重启即丢失，也不能多实例共享进度。
2. **没有取消端点**。`TrainTask#cancel()` 仅在训练线程收到 `InterruptedException` 时被调用，
   `TrainController` 未暴露 cancel 接口，长训练只能等其自然结束。
3. **`max-size` 不拦截上传**。它只被 `trainServiceInfoContributor` 读取用于服务信息展示，
   真实上限取决于宿主的 `spring.servlet.multipart.max-file-size` / `max-request-size`。
4. **训练类型只能在 PENDING 指定**。`TrainTask#selectType(String)` 有 PENDING-only 守卫；
   同时 `startTraining` 会先 `require(type)` 再 `task.start()`，因此类型未接入时任务不会被污染成 RUNNING。
5. **`yolo` 依赖本机环境**。需要可执行的 Python 与 `ultralytics`；端口协商靠子进程 stdout 首行 `PORT=`，
   等待上限 60 秒；训练轮询硬超时 6 小时（`TRAIN_TIMEOUT_MIN = 6 * 60` 分钟）。
   数据必须是 Ultralytics 布局：`images/` + `labels/` + `data.yaml`，
   `data.yaml` 递归查找深度上限 `MAX_DATA_YAML_DEPTH = 3`。
6. **`resnet50` 依赖预训练模型**。`plugin.ai.training.model-path` 指向的文件/目录不可读时，
   训练会失败在引擎内部，模块不会提前校验。
7. **线程模型是裸线程**。每个任务一个 `new Thread(...)` 且 `setDaemon(true)`，
   没有池化、没有并发上限，任务量取决于宿主机器。
8. **事件监听异常被吞**。`TrainServiceImpl#publishEvent` 捕获 `RuntimeException` 后仅 `log.warn`，
   监听器写错不会中断训练，也不会通知到调用方。
9. **`TrainTaskView.from` 假定 `progress` 非空**。`TrainTask.create` 会初始化 `TrainProgress`，
   但若宿主自行构造 `TrainTask` 的替代实现，需保证 `getProgress()` 不为 `null`。
