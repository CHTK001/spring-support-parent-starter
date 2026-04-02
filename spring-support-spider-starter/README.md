# Spring Spider Starter

`spring-support-spider-starter` 用来给原生 `Spider` / `Site` 链路补默认大脑配置。

重点：

- 业务代码不需要改成 Spring 专用 API
- 仍然直接写 `Spider.create(...).setSite(site).run()`
- 没有显式调用 `site.setBrain(...)` 时，自动回退到 Spring 配置的默认大脑
- 已显式设置 `site.setBrain(...)` 时，以业务代码为准

## 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-spider-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

## 配置示例

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

- `app-key` 和 `api-key` 都支持，内部统一回落到 `apiKey`
- `provider`、`model`、`base-url` 都会映射到 `SpiderBrainDefinition`
- 如果容器中存在 `Brain` Bean，会优先复用这个实例
- 如果容器中存在 `SpiderBrainHook` Bean，会自动追加到默认大脑配置

## 使用方式

业务代码保持原生写法即可：

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

如果需要覆盖 Spring 默认大脑，直接在业务侧显式设置：

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

1. `plugin.spider.enable=false` 时，整个 starter 不生效
2. `plugin.spider.brain.enable=false` 时，不注册默认大脑
3. `site.setBrain(...)` 优先级高于 Spring 默认大脑
4. 未显式设置站点大脑时，`SpiderBrainRegistry` 会返回 Spring 注册的默认值
