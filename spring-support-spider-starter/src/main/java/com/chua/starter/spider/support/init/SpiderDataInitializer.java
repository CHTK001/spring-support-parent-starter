package com.chua.starter.spider.support.init;

import com.alibaba.fastjson2.JSON;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.sample.SampleTaskFactory;
import com.chua.starter.spider.support.service.SpiderTaskService;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 样例数据初始化器。
 *
 * <p>当配置项 {@code spring.spider.init-sample=true} 时，在应用启动后自动创建样例任务。</p>
 *
 * <p>样例任务：
 * <ul>
 *   <li>Gitee 全部项目分页持续爬虫（SCHEDULED）</li>
 *   <li>百度网盘当前设备树爬虫（ONCE）</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderDataInitializer implements CommandLineRunner {

    @Value("${spring.spider.init-sample:false}")
    private boolean initSample;

    private final SpiderTaskService taskService;
    private final SampleTaskFactory sampleTaskFactory;

    @Override
    public void run(String... args) {
        if (!initSample) {
            return;
        }
        log.info("[Spider] spring.spider.init-sample=true，开始初始化样例任务...");
        initGiteeSample();
        initBaiduPanSample();
        log.info("[Spider] 样例任务初始化完成");
    }

    private void initGiteeSample() {
        try {
            CreateTaskResult result = sampleTaskFactory.createGiteeSample();
            SpiderFlowDefinition flow = result.flow();

            SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                    .taskName("Gitee 全部项目分页持续爬虫")
                    .entryUrl("https://gitee.com/explore/all?order=starred&page=1")
                    .description("爬取 Gitee 全部项目列表，按 star 数排序，支持分页持续采集")
                    .tags("gitee,开源,分页")
                    .authType("NONE")
                    .executionType(SpiderExecutionType.SCHEDULED)
                    .status(SpiderTaskStatus.DRAFT)
                    .version(0)
                    .build();

            taskService.saveTask(task, flow);
            log.info("[Spider] Gitee 样例任务初始化成功 taskId={}", task.getId());
        } catch (Exception e) {
            log.warn("[Spider] Gitee 样例任务初始化失败（可忽略，可能已存在）: {}", e.getMessage());
        }
    }

    private void initBaiduPanSample() {
        try {
            CreateTaskResult result = sampleTaskFactory.createBaiduPanSample();
            SpiderFlowDefinition flow = result.flow();

            SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                    .taskName("百度网盘当前设备树爬虫")
                    .entryUrl("https://pan.baidu.com")
                    .description("爬取百度网盘当前登录账号的设备树形结构数据")
                    .tags("百度网盘,设备树,登录态")
                    .authType("COOKIE")
                    .executionType(SpiderExecutionType.ONCE)
                    .status(SpiderTaskStatus.DRAFT)
                    .version(0)
                    .build();

            taskService.saveTask(task, flow);
            log.info("[Spider] 百度网盘样例任务初始化成功 taskId={}", task.getId());
        } catch (Exception e) {
            log.warn("[Spider] 百度网盘样例任务初始化失败（可忽略，可能已存在）: {}", e.getMessage());
        }
    }
}
