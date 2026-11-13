# spring-support-message-starter

统一消息推送 Starter：用「提供商表 + 模板表 + 推送日志表」三张表驱动多渠道发送，
发送动作收敛到 utils 侧的 `MessagePush` SPI，业务侧只调 `/v2/message/send` 或注入
`SysMessageService`，不直接对接各渠道 SDK。

- 包名：`com.chua.starter.message.support`
- 版本：`4.0.0.51`（模块不写 `<version>`，继承 `spring-support-parent-starter`）
- 范围：**只管「配置 + 模板渲染 + 路由 + 日志」**，具体渠道由宿主引入对应
  `utils-support-*` 渠道包提供（邮件 / 短信 / 钉钉 / 飞书 / 企微 / 站内 …）。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动装配 | `MessageAutoConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| 提供商管理 | `SysMessageProvider`、`SysMessageProviderMapper`、`SysMessageService#saveProvider` | `/v2/message/provider/**` |
| 模板管理 | `SysMessageTemplate`、`SysMessageTemplateMapper`、`SysMessageService#saveTemplate` | `/v2/message/template/**` |
| 模板渲染预览 | `TemplatePreviewRequest`、`TemplatePreviewResponse`、`SysMessageService#previewTemplate` | `/v2/message/template/preview` |
| 按模板发送 | `MessageSendRequest`、`SysMessageService#send` | `/v2/message/send` |
| 按内容发送 | `MessageContentSendRequest`、`SysMessageService#sendContent` | `/v2/message/send/content` |
| 渠道发现 | `ChannelInfo`、`SysMessageService#listChannels`（遍历 `MessagePush` 定义） | `/v2/message/channel/list` |
| 远端模板同步 | `SysMessageService#syncTemplate`（走 `MessagePush#listTemplates`） | `/v2/message/template/sync` |
| 推送日志 | `SysMessageLog`、`SysMessageLogMapper`、`SysMessageService#pageForLog`、`#cleanLog` | `/v2/message/log/page`、`/v2/message/log/clean` |
| 邮件阅读追踪 | `MessageTrackProperties`、`SysMessageService#markLogRead`、`#buildContentWithTrack` | `/v2/message/track/{token}` |
| 重复消息防抖 | `SysMessageServiceImpl#isDebounced`（按 `sys_message_provider_interval`） | 发送链路内自动生效 |
| 站内消息渠道 | `StationMessagePush`（`@Spi("station")`）、`StationMessagePush.StationPusher` | 渠道标识 `station`；执行器由 `MessageAutoConfiguration#stationMessagePusherInitializer` 注入 |
| 我的站内消息 | `SysMessageStationItem`、`SysMessageService#pageForStationMessage`、`#countStationMessageUnread`、`#markStationMessageRead` | `/v2/message/sys-message/**` |
| 统一模板库同步 | `MessageTemplateSyncer`（`SourceSyncer`，来源 `message`）、`MessageProviderSource`（`ProviderSource`） | 由 `spring-support-account-starter` 的 `SourceSyncerRegistry` 启动时拉起 |
| 服务信息 | `messageServiceInfoContributor` | 启动横幅 `message` 标志 + `/v2/message/**` endpoint |

### 1.1 三张表

建表脚本 `src/main/resources/db/init/V2.0__init_all_message.sql`（`datasource-starter`
默认扫描 `classpath*:db/init/*.sql`）；`V2.0__initdata_all_message.sql` 只有注释，
**本模块无种子数据**。

| 表名 | 说明 | 关键列 |
|---|---|---|
| `sys_message_provider` | 消息提供商（渠道凭据） | `code`（唯一）、`type`、`vender`、`app_id`/`app_secret`、`smtp_*`、`webhook_url`、`interval`、`status` |
| `sys_message_template` | 消息模板 | `provider_id`、`code`、`name`、`type`、`content`、`status`、`sort` |
| `sys_message_log` | 推送日志 | `target`、`channel`、`content`、`code`（SUCCESS/FAILURE）、`subject`、`message_id`、`batch_id`、`track_token`、`read_status`、`read_time` |

> `sys_message_template` 的 DDL 里还有 `sys_message_template_title`，但
> `SysMessageTemplate` 实体没有该字段，发送时的标题取请求级 `subject`。

### 1.2 渠道解析规则

`SysMessageServiceImpl#resolveChannel`：**先取 `sys_message_provider_vender`，为空才取
`sys_message_provider_type`**，再交给 `MessagePush.create(channel)` 找 SPI 实现。
所以「用哪个渠道实现」由提供商行决定，不看模板。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-message-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `com.chua:spring-support-common-starter` | `ReturnResult`、`@Permission`、`AuthService`、`ServiceInfoContributor` | 必需；传递 `MessagePush` SPI 所在的 `utils-support-common-starter` |
| `com.chua:spring-support-mybatis-starter` | `SysBase`、`PageResultUtils`、`ReturnPageResult`（`ReturnPageResult` 实际在 common-starter） | 必需 |
| `com.chua:spring-support-account-starter:4.0.0.51` | 统一模板库 `TemplateService`、`SourceSyncer`/`ProviderSource` 契约 | 必需（模板同步器编译依赖） |
| `com.chua:spring-support-socket-starter` | `SocketSessionTemplate`，站内消息推送执行器 | pom 直接依赖；`SocketSessionTemplate` Bean 缺失时站内渠道推送返回失败 |
| `com.chua:spring-support-oauth-client-starter` | `AuthConstant`（ADMIN / SUPER_ADMIN）、登录态 | 必需 |
| `io.swagger.core.v3:swagger-annotations` | `@Operation`、`@Schema`、`@ParameterObject` | 必需 |
| `com.baomidou:mybatis-plus-*` | Mapper / 分页 | 由 `spring-support-mybatis-starter` 传递 |

**渠道实现是可选依赖**，宿主按需引入（下表 artifactId 均为实际存在的模块，扩展名取自各自
`META-INF/extensions/com.chua.common.support.task.message.MessagePush` 中的别名）：

| 渠道（SPI 扩展名） | 依赖 artifactId |
|---|---|
| `email` | `utils-support-email-starter` |
| `alibaba-sms` | `utils-support-alibaba-starter` |
| `tencent-sms` | `utils-support-tencentcloud-starter` |
| `baidu-sms` | `utils-support-baidu-starter` |
| `dingding` | `utils-support-dingding-starter` |
| `feishu` | `utils-support-hunyuan-starter` |
| `wechat` | `utils-support-wechat-starter` |
| `webhook` | `utils-support-webhook-starter` |
| `desktop` | `utils-support-desktop-starter` |
| `opt` | `utils-support-otp-starter` |
| `station` | 本模块自带 `StationMessagePush`，但**未带 `META-INF/extensions` 索引**（见第七节第 2 条） |

## 三、配置项

前缀 `plugin.message.track`（`MessageTrackProperties.PRE`），是本模块**唯一的
`@ConfigurationProperties`**：

| key | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | 是否开启邮件阅读追踪；为 `false` 时不生成 `track_token`、不注入追踪图 |
| `base-url` | 空 | 追踪图基础地址；**为空时 `buildContentWithTrack` 直接返回原文**，即开了追踪也注入不了图（不会回退当前服务地址） |

```yaml
plugin:
  message:
    track:
      enabled: true
      base-url: "http://your-host:8080"
```

其余运行参数都不在 yaml：

- **防抖间隔**落在提供商行 `sys_message_provider_interval`（分钟）：**未配置时默认 1**，
  显式 `0` 表示不限制；只有 `sys_message_code='SUCCESS'` 的记录参与判定，失败记录允许重试。
- **SMTP / AK/SK / Webhook** 等渠道凭据全部在 `sys_message_provider` 行内，由各渠道
  `MessagePush` 实现自行读取，本模块不解析。
- 日志写入方法声明了 `taskExecutor`（`Constant.DEFAULT_EXECUTOR_TASK`，由
  `spring-support-common-starter` 的 `AsyncThreadPoolConfiguration` 提供，受 `plugin.async.*` 控制），
  但因同类自调用实际未走异步代理（见第七节第 6 条）。

## 四、快速开始

**第一步**：引入依赖（见第二节）。宿主需已具备数据源与 MyBatis-Plus；至少引入一个渠道包，
否则 `/v2/message/channel/list` 为空、发送返回「暂不支持的消息渠道」。

**第二步**：建表。引入 `spring-support-datasource-starter` 时，`db/init/*.sql` 会被自动执行；
未引入时手工执行 `V2.0__init_all_message.sql`（同目录的 `V2.0__initdata_all_message.sql`
只有注释，无种子数据，执行与否都不影响）。

**第三步**：录一条提供商 + 一条模板，然后发送：

```text
POST /v2/message/provider/save   {"sysMessageProviderCode":"smtp-main","sysMessageProviderName":"主邮件",
                                  "sysMessageProviderType":"EMAIL","sysMessageProviderVender":"email",
                                  "sysMessageProviderSmtpHost":"smtp.example.com","sysMessageProviderSmtpPort":465,
                                  "sysMessageProviderSmtpFrom":"noreply@example.com"}
POST /v2/message/template/save   {"sysMessageProviderId":1,"sysMessageTemplateCode":"SMS_CODE",
                                  "sysMessageTemplateName":"验证码","sysMessageTemplateType":"SMS",
                                  "sysMessageTemplateContent":"您的验证码是 ${code}，5 分钟内有效"}
POST /v2/message/send            {"target":["13800138000"],"sysMessageTemplateId":1,"params":{"code":"123456"}}
```

模板占位符**只允许 `${var}` 与 `{{var}}`**：保存时 `findIllegalPlaceholder` 会拒绝其它形态；
`${var}`/`{{var}}` 混用可以，预览时未填充的键会进 `unresolvedParams`。

## 五、运行示例

### 5.1 调用示例

运行期是**步骤式**：`MessageSendRequest` / `MessageContentSendRequest` / `TemplatePreviewRequest`
均为 `record`，`SysMessageService` 的方法只按签名传参，**本模块自身没有链式 API**；
链式调用都来自公共 / utils 侧：`MessageRequest.builder()...build()`（`SysMessageServiceImpl`
内部用它组装请求）、`MessageResponse.builder()...build()`，以及 `ReturnResult` 的
`@Accessors(chain = true)` 写法 `code(...).msg(...).data(...)`。

**控制器里按模板发验证码**：

```java
@Service
public class NoticeService {

    private final SysMessageService sysMessageService;   // SysMessageServiceImpl，@Service

    public NoticeService(SysMessageService sysMessageService) {
        this.sysMessageService = sysMessageService;
    }

    public void sendCode(String phone, String code) {
        ReturnResult<List<MessageResponse>> result = sysMessageService.send(
                new MessageSendRequest(new String[]{phone}, 1, "验证码", Map.of("code", code)));
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getMsg());
        }
        for (MessageResponse resp : result.getData()) {
            if (!resp.isSuccess()) {
                // 单个接收人失败也在列表里，不会整体抛异常
                log.warn("发送失败: {}", resp.getErrorMessage());
            }
        }
    }
}
```

**不走模板、直接发内容**（`providerId` 决定渠道）：

```java
sysMessageService.sendContent(new MessageContentSendRequest(
        1, new String[]{"ops@example.com"}, "告警: 磁盘 92%", "<b>磁盘使用率 92%</b>", Map.of()));
```

**发送前预览模板**：

```java
TemplatePreviewResponse preview = sysMessageService.previewTemplate(
        new TemplatePreviewRequest(1, Map.of("code", "123456"))).getData();
// preview.content() 是渲染结果，preview.unresolvedParams() 是未填充的键，
// preview.complete() 为 false 时说明模板会带着占位符发出去
```

**行为要点**：

- 同一次请求的所有接收人共享一个 `batch_id`；日志写入方法虽标了 `@Async`，但在
  `send` / `sendContent` 内部是以 `this` 直接调用的（自调用不过 Spring 代理），**实际按同步执行**。
- 模板 / 提供商 `status = 0` 时直接拒绝（"模板已停用" / "消息提供商已停用"）。
- `cleanLog` 的 `beforeTime` 必填（ISO 串），缺参返回参数错误而不是「全量清理」。
- 站内消息三个接口只查当前登录用户（`AuthService#getCurrentUser`）：未接入认证时分页返回空、
  未读数返回 `0`、标记已读返回参数错误。

### 5.2 SPI 扩展示例

渠道能力来自 utils 侧 `com.chua.common.support.task.message.MessagePush`，
按「实现 → 登记 → 生效」三步加渠道：

**① 实现**：`getProvider()` 返回渠道标识，`send` 收到的是已渲染好的内容：

```java
@Spi("acme-im")
@SpiDescribe(value = "Acme 即时消息", type = "IM", desc = "内部 IM 网关")
public class AcmeMessagePush implements MessagePush {

    @Override
    public String getProvider() {
        return "acme-im";
    }

    @Override
    public MessageResponse send(MessageRequest request) throws Exception {
        // request.getTo() / getSubject() / getContent() / getContentType()
        long start = System.currentTimeMillis();
        acmeClient.push(request.getTo(), request.getSubject(), request.getContent());
        return MessageResponse.builder()
                .success(true)
                .durationMillis(System.currentTimeMillis() - start)
                .build();
    }

    @Override
    public List<TemplateInfo> listTemplates() {
        return Collections.emptyList();     // 远端模板同步为空即"该渠道不支持模板同步"
    }

    @Override
    public TemplateInfo getTemplate(String templateId) {
        return null;
    }
}
```

**② 登记**：在 `src/main/resources/META-INF/extensions/` 下建文件
`com.chua.common.support.task.message.MessagePush`，一行一个 `别名=实现类全名`：

```text
acme-im=com.acme.support.message.AcmeMessagePush
```

> `@Spi` 只是别名声明；**没有这个文件就不会被收集**。`ServiceProvider` 的静态解析器只认
> `META-INF/services`、`META-INF/extensions`，以及「与接口同包」的扫描；
> 实现类放在 `com.chua.starter.message.support.push` 不满足同包条件。

**③ 生效**：提供商行填 `sys_message_provider_vender = acme-im`（或 `type = acme-im`），
`/v2/message/channel/list` 出现该渠道，`/v2/message/send` 即路由到它。
`MessagePush` 实例由 `MessagePush.create(channel)` → `getNewExtension` 每次新建，
无状态实现最省心。

## 六、与其它 starter 的关系

- **`spring-support-common-starter`（必需）**：`@Permission`、`AuthService`（站内消息取当前用户）、
  `ReturnPageResult`、`ServiceInfoContributor`、异步线程池 `taskExecutor`；
  `ReturnResult` 与其 `MessagePush` SPI 来自其传递的 `utils-support-common-starter`。
- **`spring-support-account-starter`（必需）**：提供统一模板库 `template_info`。
  `MessageTemplateSyncer`（`SourceSyncer`，`source="message"`）把 `sys_message_template`
  全量映射进模板库并清理来源已删除的行；`MessageProviderSource`（`ProviderSource`）把
  `sys_message_provider` 上报为模板库左栏的提供商（凭证 `ak`/`sk` 上报时置空）。
  两者由 `SourceSyncerRegistry` 在启动时执行，不依赖本模块的定时任务。
- **`spring-support-socket-starter`（必需）**：`StationMessagePush` 通过
  `SocketSessionTemplate#sendToUser(userId, "message", payload)` 推送；`MessageAutoConfiguration`
  在存在该 Bean 时用 `ApplicationRunner` 注入执行器，缺失时 `send` 返回
  「站内消息推送器未初始化（未启用 Socket 服务）」。
- **`spring-support-oauth-client-starter`（必需）**：提供 `AuthConstant.ADMIN` / `SUPER_ADMIN`
  常量用于管理接口的角色判定（注解本身是 `com.chua.starter.common.support.annotations.Permission`）；
  站内消息接口不带 `@Permission`，只依赖已登录态。
- **`spring-support-datasource-starter`（建议）**：执行 `db/init` 下的建表脚本。
- **渠道 `utils-support-*`（可选）**：见第二节表格，每个包提供 `MessagePush` 实现 +
  `META-INF/extensions` 索引。`utils-support-hunyuan-starter` 里的飞书实现
  （`FeishuMessagePush`）挂在 `feishu` 扩展名下，与包名不一致，按扩展名排查。
- **`spring-api-support-common-system-starter`（冲突）**：system 侧自带
  `SysMessageController`（`/v2/message/sys-message`）、`SysMessageAdminController`、
  `SysMessageTemplateController`、`SysMessageLogController`、`SysMessagePushController`，
  以及 4 个 `SysMessage*Mapper` 与 4 组 Service/Impl，与本模块简名相同；
  **同一上下文同时引入无法启动**，扫描期抛 `ConflictingBeanDefinitionException`，详见第七节。

## 七、已知限制

1. **与 system 模块不能共存**：`SysMessageController` 类注释写明，本模块与
   `spring-api-support-common-system-starter` 的 `SysMessage*` 在扫描期同名冲突
   （重名面覆盖 4 个 Mapper 与 4 组 Service/Impl），`allow-bean-definition-overriding`
   不救扫描期冲突；要共存必须先合并或退役其中一套。
2. **`station` 渠道在本模块内没有登记 SPI**：`StationMessagePush` 标了 `@Spi("station")`，
   但模块没有 `META-INF/extensions/com.chua.common.support.task.message.MessagePush`
   索引，实现类也不在接口同包内，因此 `MessagePush.create("station")` 取不到实现；
   要在发送链路里用到站内渠道，宿主需自行补该索引文件。类注释里的「SPI 标识 station」
   目前只对补索引后的工程成立。
3. **阅读追踪依赖 `base-url`**：`enabled=true` 但 `base-url` 为空时**不会**注入追踪图，
   也不回退当前服务地址；日志里 `track_token` 仍会生成，`read_status` 停在 0。
4. **追踪只覆盖邮件 + HTML**：`isTrackable` 要求渠道是 `email`/`smtp`，
   且内容类型是 `html`/`text/html`/`EMAIL`；按内容发送时取的是提供商 `type`，
   与模板类型可能不一致。
5. **防抖默认 1 分钟**：提供商未配 `interval` 时按 1 分钟算（实体字段初始值 `1`），
   想彻底关掉必须显式写 `0`。
6. **日志写入名为异步、实际同步**：`saveLogAsync` / `saveContentLogAsync` 标了
   `@Async(Constant.DEFAULT_EXECUTOR_TASK)`，但调用点都在本类内（`this.saveLogAsync(...)`），
   Spring AOP 代理不介入自调用，写库仍在发送线程完成；写库异常只记 log，不返回给调用方。
7. **模板同步范围有限**：`syncTemplate` 只把远端 `TemplateInfo` 按 `code` upsert，
   不支持删除远端已消失的模板；`MessageProviderSource#sync` 只登记同步意图（返回
   `updated=0`），真正的 upsert 由 `MessageTemplateSyncer` 启动全量修复。
8. **模板标题列未接线**：DDL 有 `sys_message_template_title`，实体无对应字段，
   邮件标题只能靠请求级 `subject` 传。
9. **无重试 / 无对账**：单个接收人失败只体现在返回列表与 `code=FAILURE` 日志里，
   模块不提供重发队列、状态回执拉取、账单对账。
10. **配置面只有追踪一项**：除 `plugin.message.track.*` 外，本模块没有开关类配置，
    想禁用某个渠道只能不引对应渠道包，或在提供商行把 `status` 置 0。
11. **`service.impl` 不在自动装配的扫描范围**：`MessageAutoConfiguration` 的
    `@ComponentScan` 只覆盖 `...support.controller` 与 `...support.template`，
    `SysMessageServiceImpl`（`...support.service.impl`，`@Service`）需宿主自行补扫
    `com.chua.starter.message.support.service` 或显式注册 Bean，否则 `SysMessageController`
    注入 `SysMessageService` 会失败。Mapper 侧由 `@MapperScan` 覆盖，不受影响。
