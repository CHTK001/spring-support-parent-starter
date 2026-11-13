# spring-support-account-starter

账号域 starter：统一账号（密钥 / Token / Cookie / AppKey）、渠道能力（对话 / 文生图 / 文生视频 / 图生 3D）、消息模板与临时邮箱。渠道能力归本模块，AI 侧只按 SPI 消费，不反依赖 `ai-starter`。

> 版本 `4.0.0.51`；父 POM 继承 `spring-boot-starter-parent:4.1.0`，Java 25。

## 一、能力概览

| 能力 | 入口 |
| --- | --- |
| 自动装配 | `AccountAutoConfiguration`、`TemplateAutoConfiguration`（`AutoConfiguration.imports`） |
| 控制器 | 13 个（`MailboxController`, `SmsTemplateSyncController`, `TempMailController`, `AccountAppKeyController`, `AccountController`, `AccountCookieController`） |
| Service | 9 个 |
| SPI | 11 个（`AccountInboxProvider`、`AccountVerifier`、`ChatChannelProvider`、`ImageChannelProvider`、`Model3DChannelProvider`、`VideoChannelProvider`） |
| 建表脚本 | 6 个 SQL 文件 |

## 二、引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-account-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

内部依赖：`common`、`mybatis`

## 三、装配与开关

`AccountAutoConfiguration` 与 `TemplateAutoConfiguration` 都登记在 `AutoConfiguration.imports`；没有类级开关。`@ComponentScan` 采用**类白名单**固定扫描，避免宿主 classpath 上出现兄弟模块时装配结果漂移。

## 四、公开 Bean

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `templateMybatisPlusInterceptor` | `MybatisPlusInterceptor` | 分页插件兜底。 模块的 XML 分页 SQL 依赖 MyBatis-Plus 的 PaginationInnerInterceptor； 宿主若已通过 spring-support-mybatis-starter 注册同名 Bean，本方法自动退避， 不改变既有装配。模块独立运行（仅引入 template-starter）时也能正确返回 total。 |
| `templateServiceInfoContributor` | `ServiceInfoContributor` | 注册模板模块服务信息。 |
| `sourceSyncRunner` | `ApplicationRunner` | 启动时全量来源同步器（无同步器时不执行源行修复，只打日志） |

## 五、Service（组件扫描，可直接注入）

| Service | 说明 |
| --- | --- |
| `MailboxService` | 收 / 发信箱编排服务。 收信走 AccountInboxProvider SPI（邮件由 email-imap 实现提供）， 发信统一走 utils 的 MessagePush SPI：邮件用 EmailPush（按账号的 SMTP 配置注入 MessageEnvironme… |
| `ManageAccountServiceImpl` | 统一账号服务实现。 职责边界： 落库前加密敏感值，读取后清空敏感字段，只回传 hasSecret； 按归属隔离：个人账号只对本人可见，owner 为空的部署级账号对所有登录用户可见； 校验能力走 AccountVerifier SPI，账号模块不反向依赖设备 / 邮件 / 短信… |
| `MessageTemplateService` | 短信 / 邮件消息模板服务 —— 模板的增删改查、默认模板选择与变量渲染。 职责边界： 模板元数据：按种类（SMS / EMAIL）维护多套模板，同一种类仅一条默认模板； 渲染：把 {{变量}} 占位替换为实际值，返回缺失变量名便于前端提示； 收信装饰：临时账号轮询到新短信 /… |
| `SmsTemplateSyncService` | 短信模板同步。 不绑定厂商：按账号 vendor 解析 MessagePush SPI，调用通用的 MessagePush#listTemplates()，把云端模板按 templateCode 落到 消息模板表（kind=SMS）。阿里 / 腾讯 / 百度等实现由宿主按需引入… |
| `SysAccountAppKeyServiceImpl` | 账号模块外部访问密钥服务实现。 |
| `SysAccountCookieServiceImpl` | 账号模块 Cookie 池服务实现。 |
| `SysAccountSecretServiceImpl` | 账号密钥库服务实现。 归属口径与账号 / Cookie 一致：owner 为空表示部署级共享， 非空只对本人可见可改。Secret 落库加密，读接口只回 hasSecret。 |
| `TempMailService` | 临时邮箱编排服务。 账号仍然落在统一的 sys_account 表里，只多一个 config.tempProvider 标记：列表、归属隔离、默认账号、删除墓碑等 全部复用账号模块既有逻辑，不新增表也不新增账号种类。 凭据只存一次：邮箱地址放 username，mail.tm … |
| `TemplateServiceImpl` | 模板服务实现 |

## 六、HTTP 接口

### `MailboxController`（`/api/accounts/{accountId}/mailbox`）

收 / 发信箱接口。 挂在统一账号下面：账号页右键「打开邮箱 / 打开短信」进入的就是这里的收信箱， 不跳外链、不依赖第三方 Web 控制台。 GET /api/accounts/{accountId/mailbox}：能否收 / 发信与可用文件夹； GET /api/accounts/{accountId/mailbox/messages}：某个文件夹的信件列表（不含正文）； GET /api/

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/accounts/{accountId}/mailbox` | `overview` | 信箱概览。 |
| GET | `/api/accounts/{accountId}/mailbox/messages` | `messages` | 取信列表。 |
| GET | `/api/accounts/{accountId}/mailbox/messages/{messageId}` | `message` | 单封邮件详情。 |
| POST | `/api/accounts/{accountId}/mailbox/messages/{messageId}/seen` | `markSeen` | 设置已读 / 未读标记。 |
| POST | `/api/accounts/{accountId}/mailbox/messages/{messageId}/delete` | `delete` | 删除单封邮件。 用 POST 而不是 DELETE：消息标识可能含特殊字符，且部分网关会丢掉 DELETE 的查询参数。 |
| POST | `/api/accounts/{accountId}/mailbox/send` | `send` | 发信。 |

### `SmsTemplateSyncController`（`/api/accounts`）

短信模板同步接口。 按账号 vendor 解析 MessagePush SPI，拉取厂商云端的审核通过模板， 落到消息模板表（kind=SMS）；不依赖具体厂商。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/accounts/{accountId}/sms/templates/sync` | `sync` | 同步指定短信账号的云端模板。 |

### `TempMailController`（`/api/accounts/temp-mail`）

临时邮箱接口。 临时邮箱是账号资产的一种：不新增账号种类，靠 config.tempProvider 标记，因此这里只提供「领取 / 列表 / 销毁」， 收信直接走统一账号的 /api/accounts/{accountId/mailbox}。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/accounts/temp-mail` | `claim` | 领取临时邮箱。 |
| GET | `/api/accounts/temp-mail` | `list` | 当前用户可见的临时邮箱。 |
| DELETE | `/api/accounts/temp-mail/{accountId}` | `destroy` | 销毁临时邮箱（远端账号 + 本地逻辑删除）。 |

### `AccountAppKeyController`（`/api/accounts/app-keys`）

账号模块外部访问密钥管理接口。 与 AI 的模型计费密钥无关，是账号能力（设备 / 脚本 / 外部系统）的访问凭据： GET /api/accounts/app-keys：列表； POST /api/accounts/app-keys：新增，返回一次性明文 Secret； PUT /api/accounts/app-keys/{appKeyId}：更新名称 / 状态 / 过期 / 白名单； POS

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/accounts/app-keys` | `list` | 密钥列表。 |
| POST | `/api/accounts/app-keys` | `create` | 新增密钥。 |
| PUT | `/api/accounts/app-keys/{appKeyId}` | `update` | 更新密钥属性。 |
| POST | `/api/accounts/app-keys/{appKeyId}/rotate` | `rotate` | 轮换 Secret。 |
| DELETE | `/api/accounts/app-keys/{appKeyId}` | `delete` | 删除密钥。 |

### `AccountController`（`/api/accounts`）

统一账号管理接口。 账号是设备、模板等必选依赖模块的公共凭据来源，也是账号管理页的主体： GET /api/accounts：账号列表（支持种类与关键词过滤）； GET /api/accounts/kinds：受支持的账号种类； POST /api/accounts：新增账号； PUT /api/accounts/{accountId}：更新账号； DELETE /api/accounts/{ac

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/accounts` | `list` | 账号列表。 |
| GET | `/api/accounts/kinds` | `kinds` | 受支持的账号种类。 |
| GET | `/api/accounts/{accountId}` | `get` | 账号详情。 |
| POST | `/api/accounts` | `create` | 新增账号。 |
| PUT | `/api/accounts/{accountId}` | `update` | 更新账号。 |
| DELETE | `/api/accounts/{accountId}` | `delete` | 删除账号。 |
| POST | `/api/accounts/{accountId}/verify` | `verify` | 校验账号凭据。 |
| POST | `/api/accounts/batch/status` | `batchStatus` | 批量启用 / 停用账号。 |
| POST | `/api/accounts/batch/verify` | `batchVerify` | 批量校验账号凭据（账号健康巡检）。 |
| POST | `/api/accounts/batch/delete` | `batchDelete` | 批量删除账号（逻辑删除）。 用 POST 而不是带请求体的 DELETE：后者在部分网关与客户端上会被丢掉请求体。 |

### `AccountCookieController`（`/api/accounts/cookies`）

账号模块 Cookie 池管理接口。 供免登录渠道与会话型站点保存登录 Cookie，内容加密落库、接口不回显： GET /api/accounts/cookies：列表； POST /api/accounts/cookies：新增； PUT /api/accounts/cookies/{cookieId}：更新； DELETE /api/accounts/cookies/{cookieId}：逻

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/accounts/cookies` | `list` | Cookie 列表。 |
| GET | `/api/accounts/cookies/{cookieId}` | `get` | Cookie 详情。 |
| POST | `/api/accounts/cookies` | `create` | 新增 Cookie。 |
| PUT | `/api/accounts/cookies/{cookieId}` | `update` | 更新 Cookie。 |
| DELETE | `/api/accounts/cookies/{cookieId}` | `delete` | 删除 Cookie。 |

### `AccountSecretController`（`/api/accounts/secrets`）

账号模块密钥库管理接口。 密钥是账号 / 设备 / server 共用的底层凭据： GET /api/accounts/secrets：列表； POST /api/accounts/secrets：新增； PUT /api/accounts/secrets/{secretId}：更新； DELETE /api/accounts/secrets/{secretId}：逻辑删除。 Secret 只进不

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/accounts/secrets` | `list` | 密钥列表。 |
| GET | `/api/accounts/secrets/{secretId}` | `get` | 密钥详情。 |
| POST | `/api/accounts/secrets` | `create` | 新增密钥。 |
| PUT | `/api/accounts/secrets/{secretId}` | `update` | 更新密钥。 |
| DELETE | `/api/accounts/secrets/{secretId}` | `delete` | 删除密钥。 |

### `MessageTemplateController`（`/api/channels/templates`）

短信 / 邮件消息模板管理接口。 短信 / 邮件消息模板管理接口，归「模板管理」域（不再挂渠道管理页）。 模板把渠道收到的原始消息渲染成可读文本；供短信接码 / 邮件收信能力的 模板管理页（MessageTemplateView，@pages/template）使用： GET /api/channels/templates?kind=SMS|EMAIL：模板列表； POST /api/channe

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/channels/templates` | `list` | 模板列表。 |
| POST | `/api/channels/templates` | `save` | 新增或更新模板。 |
| DELETE | `/api/channels/templates/{templateId}` | `delete` | 删除模板。 |
| POST | `/api/channels/templates/{templateId}/default` | `setDefault` | 设为该种类默认模板。 |
| POST | `/api/channels/templates/render` | `render` | 渲染预览：按模板内容与变量生成最终文本，并回报缺失变量。 |
| GET | `/api/channels/templates/variables` | `variables` | 模板内置变量清单。 |

### `ChatChannelController`（`/api/channels`）

免登录对话渠道（ChatClient）控制器 —— 渠道管理页的「模型」与「试对话」入口。 与 /api/channels 的账号类接口并列：本控制器的渠道来自 ChatChannelProvider SPI（kind=CHAT），没有绑定 / 刷新 / 签到语义， 只提供两类动作： GET /{channel/chat-models}：实时读取客户端内置模型表； POST /{channel/c

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/channels/{channel}/chat-models` | `models` | 读取免登录对话渠道的可用模型。 底层 ChatClient 扩展未引入（如宿主工程没有依赖 duckduckgo 模块）时， 不返回 500，而是给出 available=false 与原因，前端据此提示「渠道未就绪」… |
| POST | `/api/channels/{channel}/chat` | `chat` | 使用该渠道的某个模型发起一次同步对话。 请求体：{prompt: string, model?: string, system?: string, token?: string}。 返回体：{ok, channel, … |
| GET | `/api/channels/chat-channels` | `listChatChannels` | 可选模型清单（前端不需要额外过滤时的便捷入口）。 |

### `ImageChannelController`（`/api/channels`）

文生图渠道（ImageChannelProvider）控制器 —— 渠道管理页的「出图」入口。 与 /api/channels 的账号类接口、/{channel/chat} 的对话接口并列： 本控制器只处理图生图相关动作，渠道来自 ImageChannelProvider SPI（kind=IMAGE）。 GET /{channel/image-models}：可选出图模型（免登录渠道多为静态表）

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/channels/{channel}/image-models` | `models` | 读取文生图渠道的可选模型。 模型清单异常（如宿主未引入对应 ChatClient 模块）不返回 500： 与对话渠道一致，降级为空列表 + 原因，前端据此展示占位提示。 |
| POST | `/api/channels/{channel}/images` | `generate` | 生成图片。 请求体：{prompt, ratio?, n?, width?, height?, model?, token?}； 返回体：{ok, channel, prompt, images:[{key,url,t… |
| GET | `/api/channels/image-channels` | `listImageChannels` | 全部文生图渠道元数据（前端渠道卡片兜底来源）。 |

### `Model3DChannelController`（`/api/channels`）

图生 3D 渠道（Model3DChannelProvider）控制器 —— 渠道管理页的「3D 模型」入口。 GET /{channel/3d-models}：可选的 3D 格式 / 模型； POST /{channel/3d-models}：按参考图生成 3D 模型； GET /3d-channels：全部图生 3D 渠道元数据。 请求体里的 endpoint / token 用于直连型渠道（

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/channels/{channel}/3d-models` | `models` | 读取图生 3D 渠道的可选模型 / 格式。 |
| POST | `/api/channels/{channel}/3d-models` | `generate` | 生成 3D 模型。 请求体：{refImageUrl, prompt?, format?, timeoutSeconds?, model?, endpoint?, token?}； 返回体：{ok, channel, … |
| GET | `/api/channels/3d-channels` | `listModel3DChannels` | 全部图生 3D 渠道元数据。 |

### `VideoChannelController`（`/api/channels`）

文生视频渠道（VideoChannelProvider）控制器 —— 渠道管理页的「出视频」入口。 GET /{channel/video-models}：可选的视频模型； POST /{channel/videos}：按提示词生成视频； GET /video-channels：全部文生视频渠道元数据。 需要登录态的渠道（豆包 / 通义）由请求体临时携带 token（站点 Cookie）， 本次调

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/channels/{channel}/video-models` | `models` | 读取文生视频渠道的可选模型。 |
| POST | `/api/channels/{channel}/videos` | `generate` | 生成视频。 请求体：{prompt, ratio?, cameraMovement?, refImageKey?, timeoutSeconds?, model?, token?}； 返回体：{ok, channel,… |
| GET | `/api/channels/video-channels` | `listVideoChannels` | 全部文生视频渠道元数据。 |

### `TemplateController`（`/api/template`）

模板管理 控制器 模块前缀由 httpConfig.moduleBaseUrl.template 决定（默认 /api/template）； 端点对齐设备模块，分页走 POST/GET /page。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/template/page` | `pageTemplates` | 分页查询模板 |
| GET | `/api/template/list` | `listTemplates` | 模板列表（不分页） |
| GET | `/api/template/{id}` | `getTemplate` | 获取模板 |
| GET | `/api/template/{id}/detail` | `templateDetail` | 模板详情（按模板 ID 取信息） 回答「拿到这个模板之后需要什么」：自定义模板给出设计器产物；固定模板 （三方同步）给出三方模板 ID、归属账号、推送渠道与占位符参数，字段形态对齐 MessagePush 发送一条模板… |
| GET | `/api/template/{id}/designer` | `designerTemplate` | 获取模板设计器数据 |
| POST | `/api/template/add` | `addTemplate` | 新增模板 |
| PUT | `/api/template/{id}` | `updateTemplate` | 更新模板 |
| DELETE | `/api/template/{id}` | `deleteTemplate` | 删除模板 |
| GET | `/api/template/printer` | `printerList` | 打印机列表（应用側 hiprint 连接地址与 Token） |
| GET | `/api/template/printer/discover` | `discoverPrinters` | 自发现打印机 |
| POST | `/api/template/printer/default` | `setDefaultPrinter` | 设置默认打印机 |
| DELETE | `/api/template/printer/{name}` | `removePrinter` | 删除打印机 |
| POST | `/api/template/printer` | `setPrinter` | 设置打印机参数（包含地址/端口/Token） |
| POST | `/api/template/sync` | `syncAllSources` | 来源同步：恢复所有来源（message / paper）到统一模板库，返回各来源模板数 |
| POST | `/api/template/sync/{source}` | `syncSource` | 来源同步：恢复指定来源（message / paper）到统一模板库，返回该来源模板数 |
| GET | `/api/template/sync/sources` | `syncSources` | 来源同步器信息（每个来源的已注册数与库中模板数） |

## 七、配置

本模块没有 `@ConfigurationProperties`。

## 八、数据表与脚本

| 脚本 | 建表数 | 表名（前 12 个） |
| --- | --- | --- |
| `db/init/V2.0__init_all_template.sql` | 4 | `template_info`, `template_printer`, `template_provider`, `template_log` |
| `db/init/V2.1__add_template_source.sql` | 0 | — |
| `db/init/V2.4__add_template_source_code.sql` | 0 | — |
| `db/init/V6.4__add_channel_message_template.sql` | 1 | `sys_ai_channel_message_template` |
| `db/init/V6.5__add_account.sql` | 3 | `sys_account`, `sys_account_app_key`, `sys_account_cookie` |
| `db/init/V6.6__add_account_secret.sql` | 1 | `sys_account_secret` |

## 九、SPI 扩展点

- `AccountInboxProvider`：`META-INF/extensions/com.chua.starter.account.spi.AccountInboxProvider`
- `AccountVerifier`：`META-INF/extensions/com.chua.starter.account.spi.AccountVerifier`
- `ChatChannelProvider`：`META-INF/extensions/com.chua.starter.account.spi.ChatChannelProvider`
- `ImageChannelProvider`：`META-INF/extensions/com.chua.starter.account.spi.ImageChannelProvider`
- `Model3DChannelProvider`：`META-INF/extensions/com.chua.starter.account.spi.Model3DChannelProvider`
- `VideoChannelProvider`：`META-INF/extensions/com.chua.starter.account.spi.VideoChannelProvider`
- `AccountChannelAuthManager`：`META-INF/extensions/com.chua.starter.common.support.channel.AccountChannelAuthManager`
- `ChannelCreditProvider`：`META-INF/extensions/com.chua.starter.common.support.channel.ChannelCreditProvider`
- `ChannelUpstreamAdmin`：`META-INF/extensions/com.chua.starter.common.support.channel.ChannelUpstreamAdmin`
- `ChannelUpstreamCodec`：`META-INF/extensions/com.chua.starter.common.support.channel.ChannelUpstreamCodec`
- `ChannelUpstreamProvider`：`META-INF/extensions/com.chua.starter.common.support.channel.ChannelUpstreamProvider`

## 十、通用场景

AI 控制台、设备台要接渠道时，注入账号域提供的 Service 或按 `ChatChannelProvider` 等 SPI 消费；发验证码、通知走 `MessageTemplateService`；注册收码用临时邮箱（`TempMailService`）。

## 十一、注意事项

- 账号数据按 owner 隔离；写接口带归属校验，跨用户写会被拒绝。
- 渠道能力（聊天、出图、视频、3D）只在账号模块维护，`ai-starter` 不复制协议实现。
- 临时邮箱依赖外部服务，失败时应降级到普通邮箱流程。

## 十二、验证

```bash
mvn -pl spring-support-account-starter -am -DskipTests package
```

接口路径以本文档为准，启动后用 `GET /service-info` 查看模块开关与端点汇总。
