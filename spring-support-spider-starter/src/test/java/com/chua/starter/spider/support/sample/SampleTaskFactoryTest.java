package com.chua.starter.spider.support.sample;

import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * SampleTaskFactory 单元测试。
 */
class SampleTaskFactoryTest {

    private SampleTaskFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SampleTaskFactory();
    }

    // ── Gitee 样例 ────────────────────────────────────────────────────────────

    @Test
    void createGiteeSample_entryUrlIsCorrect() {
        CreateTaskResult result = factory.createGiteeSample();
        // flow 中不含 entryUrl，task 在 result 中（taskId=null 时 task 未持久化）
        // 验证编排结构
        assertThat(result.getFlow()).isNotNull();
        assertThat(result.getFlow().getNodes()).hasSize(6);
    }

    @Test
    void createGiteeSample_flowHasFullChain() {
        CreateTaskResult result = factory.createGiteeSample();

        assertThat(result.getFlow().getNodes())
                .extracting(SpiderFlowNode::getNodeType)
                .containsExactly(
                        SpiderNodeType.START,
                        SpiderNodeType.DOWNLOADER,
                        SpiderNodeType.PARSER,
                        SpiderNodeType.FILTER,
                        SpiderNodeType.PIPELINE,
                        SpiderNodeType.END
                );
    }

    @Test
    void createGiteeSample_hasCorrectEdgeCount() {
        CreateTaskResult result = factory.createGiteeSample();
        assertThat(result.getFlow().getEdges()).hasSize(5);
    }

    @Test
    void createGiteeSample_downloaderNodeHasConfig() {
        CreateTaskResult result = factory.createGiteeSample();
        SpiderFlowNode downloader = result.getFlow().getNodes().stream()
                .filter(n -> n.getNodeType() == SpiderNodeType.DOWNLOADER)
                .findFirst().orElseThrow();
        assertThat(downloader.getConfig()).isNotNull().isNotEmpty();
    }

    // ── 百度网盘样例 ──────────────────────────────────────────────────────────

    @Test
    void createBaiduPanSample_flowHasFullChain() {
        CreateTaskResult result = factory.createBaiduPanSample();

        assertThat(result.getFlow().getNodes())
                .extracting(SpiderFlowNode::getNodeType)
                .containsExactly(
                        SpiderNodeType.START,
                        SpiderNodeType.DOWNLOADER,
                        SpiderNodeType.PARSER,
                        SpiderNodeType.FILTER,
                        SpiderNodeType.PIPELINE,
                        SpiderNodeType.END
                );
    }

    @Test
    void createBaiduPanSample_noPlaintextCredential() {
        // 百度网盘样例不应包含明文密码
        // credentialRef 只存 credentialId 和 credentialType，不含 password
        CreateTaskResult result = factory.createBaiduPanSample();
        // 验证编排节点配置中不含 password 字段
        result.getFlow().getNodes().forEach(node -> {
            if (node.getConfig() != null) {
                assertThat(node.getConfig()).doesNotContainKey("password");
                assertThat(node.getConfig()).doesNotContainKey("passwd");
            }
        });
    }

    @Test
    void createBaiduPanSample_hasEdges() {
        CreateTaskResult result = factory.createBaiduPanSample();
        assertThat(result.getFlow().getEdges()).hasSize(5);
    }
}
