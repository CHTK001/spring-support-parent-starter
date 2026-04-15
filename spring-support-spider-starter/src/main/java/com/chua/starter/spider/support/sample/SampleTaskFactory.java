package com.chua.starter.spider.support.sample;

import com.alibaba.fastjson2.JSON;
import com.chua.starter.spider.support.domain.SpiderCredentialRef;
import com.chua.starter.spider.support.domain.SpiderExecutionPolicy;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 样例任务工厂。
 *
 * <p>提供内置样例任务，用于快速验证平台核心功能链路。</p>
 *
 * @author CH
 */
public class SampleTaskFactory {

    /**
     * 创建 Gitee 全部项目分页持续爬虫样例。
     *
     * <ul>
     *   <li>入口 URL：{@code https://gitee.com/explore/all?order=starred&page=1}</li>
     *   <li>执行类型：SCHEDULED（每小时一次）</li>
     *   <li>编排：START → DOWNLOADER → PARSER → FILTER → PIPELINE → END</li>
     * </ul>
     */
    public CreateTaskResult createGiteeSample() {
        String taskCode = "SAMPLE-GITEE-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .executionType(SpiderExecutionType.SCHEDULED)
                .cron("0 0 * * * ?")
                .timezone("Asia/Shanghai")
                .misfirePolicy("DO_NOTHING")
                .jobChannel("default")
                .threadCount(2)
                .build();

        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode(taskCode)
                .taskName("Gitee 全部项目分页持续爬虫")
                .entryUrl("https://gitee.com/explore/all?order=starred&page=1")
                .description("爬取 Gitee 全部项目列表，按 star 数排序，支持分页持续采集")
                .tags("gitee,开源,分页")
                .authType("NONE")
                .executionType(SpiderExecutionType.SCHEDULED)
                .executionPolicy(JSON.toJSONString(policy))
                .status(SpiderTaskStatus.DRAFT)
                .version(0)
                .build();

        SpiderFlowDefinition flow = buildFullChainFlow(
                "gitee-start", "gitee-dl", "gitee-parser", "gitee-filter", "gitee-pipeline", "gitee-end",
                Map.of("urlPattern", "https://gitee.com/explore/all?order=starred&page={page}",
                        "maxPages", 100,
                        "downloader", "jsoup"),
                Map.of("selector", ".explore-repo__list .item", "type", "CSS",
                        "fields", List.of("name", "description", "stars", "url")),
                Map.of("dedup", true, "dedupField", "url"),
                Map.of("pipeline", "json", "outputPath", "./data/gitee-projects")
        );

        return new CreateTaskResult(null, flow);
    }

    /**
     * 创建百度网盘当前设备树爬虫样例。
     *
     * <ul>
     *   <li>入口 URL：{@code https://pan.baidu.com}</li>
     *   <li>执行类型：ONCE</li>
     *   <li>认证方式：凭证引用（不含明文密码）</li>
     * </ul>
     */
    public CreateTaskResult createBaiduPanSample() {
        String taskCode = "SAMPLE-BAIDUPAN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        SpiderExecutionPolicy policy = SpiderExecutionPolicy.builder()
                .executionType(SpiderExecutionType.ONCE)
                .threadCount(1)
                .manualTrigger(true)
                .build();

        // 凭证引用（不存储明文密码）
        SpiderCredentialRef credentialRef = SpiderCredentialRef.builder()
                .credentialId("baidu-pan-credential")
                .credentialType("COOKIE")
                .build();

        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode(taskCode)
                .taskName("百度网盘当前设备树爬虫")
                .entryUrl("https://pan.baidu.com")
                .description("爬取百度网盘当前登录账号的设备树形结构数据")
                .tags("百度网盘,设备树,登录态")
                .authType("COOKIE")
                .executionType(SpiderExecutionType.ONCE)
                .executionPolicy(JSON.toJSONString(policy))
                .credentialRef(JSON.toJSONString(credentialRef))
                .status(SpiderTaskStatus.DRAFT)
                .version(0)
                .build();

        SpiderFlowDefinition flow = buildFullChainFlow(
                "pan-start", "pan-dl", "pan-parser", "pan-filter", "pan-pipeline", "pan-end",
                Map.of("downloader", "playwright", "headless", false,
                        "waitForSelector", ".device-tree", "javascriptEnabled", true),
                Map.of("selector", ".device-tree .node", "type", "CSS",
                        "fields", List.of("deviceName", "deviceId", "path")),
                Map.of("dedup", true, "dedupField", "deviceId"),
                Map.of("pipeline", "json", "outputPath", "./data/baidu-pan-devices")
        );

        return new CreateTaskResult(null, flow);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SpiderFlowDefinition buildFullChainFlow(
            String startId, String dlId, String parserId, String filterId, String pipelineId, String endId,
            Map<String, Object> dlConfig, Map<String, Object> parserConfig,
            Map<String, Object> filterConfig, Map<String, Object> pipelineConfig) {

        List<SpiderFlowNode> nodes = List.of(
                SpiderFlowNode.builder().nodeId(startId).nodeType(SpiderNodeType.START)
                        .label("开始").positionX(100.0).positionY(200.0).build(),
                SpiderFlowNode.builder().nodeId(dlId).nodeType(SpiderNodeType.DOWNLOADER)
                        .label("下载器").config(dlConfig).positionX(250.0).positionY(200.0).build(),
                SpiderFlowNode.builder().nodeId(parserId).nodeType(SpiderNodeType.PARSER)
                        .label("解析器").config(parserConfig).positionX(400.0).positionY(200.0).build(),
                SpiderFlowNode.builder().nodeId(filterId).nodeType(SpiderNodeType.FILTER)
                        .label("过滤器").config(filterConfig).positionX(550.0).positionY(200.0).build(),
                SpiderFlowNode.builder().nodeId(pipelineId).nodeType(SpiderNodeType.PIPELINE)
                        .label("管道").config(pipelineConfig).positionX(700.0).positionY(200.0).build(),
                SpiderFlowNode.builder().nodeId(endId).nodeType(SpiderNodeType.END)
                        .label("结束").positionX(850.0).positionY(200.0).build()
        );

        List<SpiderFlowEdge> edges = List.of(
                SpiderFlowEdge.builder().edgeId("e1").sourceNodeId(startId).targetNodeId(dlId).build(),
                SpiderFlowEdge.builder().edgeId("e2").sourceNodeId(dlId).targetNodeId(parserId).build(),
                SpiderFlowEdge.builder().edgeId("e3").sourceNodeId(parserId).targetNodeId(filterId).build(),
                SpiderFlowEdge.builder().edgeId("e4").sourceNodeId(filterId).targetNodeId(pipelineId).build(),
                SpiderFlowEdge.builder().edgeId("e5").sourceNodeId(pipelineId).targetNodeId(endId).build()
        );

        return SpiderFlowDefinition.builder().nodes(nodes).edges(edges).version(1).build();
    }
}
