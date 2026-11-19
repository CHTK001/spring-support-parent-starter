# account-starter 定时任务索引

本模块全部后台定时功能统一收录在本包（`com.chua.starter.account.schedule`）。
查找顺序：先看本清单，再进对应类。两个任务类均自带 `@EnableScheduling`，
并由 `@ConditionalOnProperty` 门控（默认关闭）：开关不开，Bean 不注册，
定时任务也不会带进宿主（依赖即带入 / R5 副作用控制）。

## 专属任务类（本包内）

| 类 | 业务 | 触发方式 | 周期 | 开关 / 配置键 |
|---|---|---|---|---|
| `SmsDirectoryPoller` | 短信目录轮询（PollDirectory 接入，不依赖外部平台）：扫描 `plugin.account.sms-directory.dir`（默认 `./sms-inbox`）下的 `*.json`，命中临时手机号后写入内存暂存库并经 SSE 广播 `new-sms`；文件按解析结果归档到 `done/` / `error/` | `@Scheduled(fixedDelayString)`，类上 `@EnableScheduling` | `plugin.account.sms-directory.interval.ms`，默认 5000ms | `plugin.account.sms-directory.enable`（默认 `false`） |
| `MailNewMessagePoller` | 新邮件轮询：按账号配置 `notifyNew` 轮询邮箱新邮件，经 Socket（`SocketNewMailPublisher`）与 SSE 双通道广播 `new-mail`；首次轮询只记基线不推历史 | `@Scheduled(fixedDelayString)`，类上 `@EnableScheduling` | `plugin.account.mail-notify.interval.ms`，默认 60000ms | `plugin.account.mail-notify.enable`（默认 `false`）；另需 classpath 有 spring-support-socket-starter（`@ConditionalOnClass(SocketSessionTemplate)`） |

## 内联 @Scheduled（留在业务类，未搬移）

无。本模块当前仅上述两个定时任务，且均为独立任务类，已全部收拢到本包。

## @EnableScheduling 入口

- `schedule.SmsDirectoryPoller`（短信目录轮询线）
- `schedule.MailNewMessagePoller`（新邮件轮询线）

> 注意：两个任务都是「显式开启才装配」，宿主默认没有任何账号侧定时任务在跑。
> 新增定时任务请放本包并更新本清单；与实例状态强耦合、无法干净抽离的任务
> 才允许内联留在业务类，但必须回到本清单登记。
