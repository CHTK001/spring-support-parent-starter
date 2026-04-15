# Spring Spider Starter

## 当前说明

`spring-support-spider-starter` 当前仓库实现仍以“Spider 默认大脑注入”能力为主，但产品目标已经升级为 `Spider Platform Starter`。

也就是说：

- 当前代码基线：默认 Spider Brain 配置、Spring 自动注册、环境配置透传。
- 目标设计基线：蜘蛛控制台、任务编排、AI 大脑、调度绑定、运行时回写。

为了避免继续被旧描述带偏，后续实现与验收统一以以下文档为准：

- [爬虫设计文档](./爬虫设计文档.md)
- [爬虫任务清单](./爬虫任务清单.md)
- [爬虫测试清单](./爬虫测试清单.md)

## 当前已存在能力

- 业务代码仍可直接使用原生 `Spider.create(...).setSite(site).run()`。
- 未显式设置 `site.setBrain(...)` 时，可回退到 Spring 注册的默认 Brain。
- 已显式设置 `site.setBrain(...)` 时，以业务侧配置为准。

## 当前依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-spider-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

## 当前配置示例

```yaml
plugin:
  spider:
    enable: true
    brain:
      enable: true
      provider: siliconflow
      app-key: ${SILICONFLOW_API_KEY:}
      base-url: https://api.siliconflow.cn/v1/chat/completions
      model: Qwen/Qwen2.5-7B-Instruct
      system-prompt: 你是网页抽取助手，只返回结构化结果
      session-id: spider-default
      takeover: true
```

说明：

- `app-key` 和 `api-key` 都支持，内部统一回落到 `apiKey`。
- `provider`、`model`、`base-url` 会映射到 `SpiderBrainDefinition`。
- 如果容器中存在 `Brain` Bean，会优先复用该实例。
- 如果容器中存在 `SpiderBrainHook` Bean，会自动追加到默认 Brain 配置。

## 当前使用方式

```java
Site site = Site.me()
        .setDomain("gitee.com")
        .setUserAgent("demo-spider/1.0");

Spider.create(new DemoPageProcessor(site))
        .setSite(site)
        .thread(1)
        .addUrl("https://gitee.com/dromara/Jpom")
        .run();
```

如果需要覆盖 Spring 默认 Brain，业务侧仍可显式设置：

```java
site.setBrain(customBrain);
```

或者：

```java
Spider.create(processor)
        .setSite(site)
        .setBrain(customBrain)
        .run();
```

## 生效规则

1. `plugin.spider.enable=false` 时，整个 starter 不生效。
2. `plugin.spider.brain.enable=false` 时，不注册默认 Brain。
3. `site.setBrain(...)` 优先级高于 Spring 默认 Brain。
4. 未显式设置站点大脑时，`SpiderBrainRegistry` 返回 Spring 注册的默认值。
