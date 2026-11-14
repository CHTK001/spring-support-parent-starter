package com.chua.starter.monitor.support.server;

import com.chua.starter.monitor.support.server.model.ServerNodeSummary;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页口径的单测：过滤 / 排序 / 切片 / 统计。
 *
 * <p>这一层是「用户唯一能看到的真相」：筛了协议却出现别的协议、翻到第 2 页却
 * 还是第 1 页的数据、左栏显示 3 个运行中而表格里 4 个绿点 —— 这些都不会报错，
 * 只有人对着屏幕才发现。所以把它抽成纯函数单独锁住。</p>
 *
 * <p>另外两条口径也在这里断言：状态是运行时值，枚举外的历史数据必须计入
 * {@code unknown}（否则分项之和 ≠ 总数）；过滤器计数按挂载数累加，不按启用去重。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@DisplayName("节点分页口径")
class ServerNodePagerTest {

    private static ServerNodeView node(long id, String name, String protocol, Integer port,
                                       String status, int filterCount) {
        List<com.chua.starter.monitor.support.server.model.ServerFilterDescriptor> filters =
                new java.util.ArrayList<>();
        for (int i = 0; i < filterCount; i++) {
            filters.add(new com.chua.starter.monitor.support.server.model.ServerFilterDescriptor(
                    "f" + i, "file-storage", i, Boolean.TRUE, Map.of()));
        }
        return new ServerNodeView(id, name, protocol, protocol + " 描述", "jdk", "0.0.0.0", port,
                1, null, Map.of(), filters, status, "RUNNING".equals(status) ? null : "BindException",
                "http://127.0.0.1:" + port + "/");
    }

    @Test
    @DisplayName("关键字命中名称、端口与报错文本")
    void filterByKeyword() {
        List<ServerNodeView> views = List.of(
                node(1, "acc-server", "HTTP", 18080, "RUNNING", 1),
                node(2, "file-server", "HTTP", 19090, "STOPPED", 0));

        assertEquals(1, ServerNodePager.filter(views, "acc", null, null).size());
        assertEquals(2, ServerNodePager.filter(views, "server", null, null).size());
        assertEquals(1, ServerNodePager.filter(views, "19090", null, null).size());
        // 报错文本也参与匹配：排障时用户搜的就是 BindException
        assertEquals(1, ServerNodePager.filter(views, "bindexception", null, null).size());
    }

    @Test
    @DisplayName("协议与状态精确匹配，空值表示不过滤")
    void filterByProtocolAndStatus() {
        List<ServerNodeView> views = List.of(
                node(1, "a", "HTTP", 18080, "RUNNING", 0),
                node(2, "b", "HTTP_PROXY", 18081, "ERROR", 0),
                node(3, "c", "HTTP", 18082, "STOPPED", 0));

        assertEquals(2, ServerNodePager.filter(views, null, "HTTP", null).size());
        assertEquals(1, ServerNodePager.filter(views, null, "HTTP_PROXY", null).size());
        assertEquals(1, ServerNodePager.filter(views, null, null, "ERROR").size());
        // HTTP 不能按前缀命中 HTTP_PROXY：精确比较而不是 startsWith
        assertEquals(3, ServerNodePager.filter(views, "", "", "").size());
    }

    @Test
    @DisplayName("状态排序：异常 → 运行中 → 已停止，未知状态排最后")
    void sortByStatus() {
        List<ServerNodeView> views = new java.util.ArrayList<>(List.of(
                node(1, "c", "HTTP", 18082, "STOPPED", 0),
                node(2, "a", "HTTP", 18080, "RUNNING", 0),
                node(3, "b", "HTTP", 18081, "ERROR", 0),
                node(4, "d", "HTTP", 18083, "WEIRD", 0)));

        ServerNodePager.sort(views, "status");
        assertEquals(List.of("b", "a", "c", "d"),
                views.stream().map(ServerNodeView::name).toList());
    }

    @Test
    @DisplayName("按名称排序时忽略大小写")
    void sortByNameIsCaseInsensitive() {
        List<ServerNodeView> views = new java.util.ArrayList<>(List.of(
                node(1, "zeta", "HTTP", 1, "RUNNING", 0),
                node(2, "Alpha", "HTTP", 2, "RUNNING", 0)));

        ServerNodePager.sort(views, "name");
        assertEquals(List.of("Alpha", "zeta"), views.stream().map(ServerNodeView::name).toList());
    }

    @Test
    @DisplayName("翻页切片：越界返回空，末页只取剩余部分")
    void slicePages() {
        List<Integer> list = List.of(1, 2, 3, 4, 5);

        assertEquals(List.of(1, 2), ServerNodePager.slice(list, 1, 2));
        assertEquals(List.of(5), ServerNodePager.slice(list, 3, 2));
        assertTrue(ServerNodePager.slice(list, 4, 2).isEmpty());
        assertTrue(ServerNodePager.slice(List.of(), 1, 20).isEmpty());
    }

    @Test
    @DisplayName("页码与每页条数兜底：非法值回到默认，超上限被钳制")
    void clampPageAndSize() {
        assertEquals(1, ServerNodePager.pageOf(null));
        assertEquals(1, ServerNodePager.pageOf(0));
        assertEquals(3, ServerNodePager.pageOf(3));

        assertEquals(20, ServerNodePager.sizeOf(null));
        assertEquals(20, ServerNodePager.sizeOf(-1));
        assertEquals(ServerNodePager.MAX_PAGE_SIZE, ServerNodePager.sizeOf(10_000));
        assertEquals(50, ServerNodePager.sizeOf(50));
    }

    @Test
    @DisplayName("统计：未知状态单独计数，过滤器按挂载数累加")
    void summarizeCounts() {
        List<ServerNodeView> views = List.of(
                node(1, "a", "HTTP", 1, "RUNNING", 2),
                node(2, "b", "HTTP", 2, "STOPPED", 1),
                node(3, "c", "HTTP", 3, "ERROR", 0),
                node(4, "d", "HTTP", 4, "LEGACY", 3));

        ServerNodeSummary summary = ServerNodePager.summarize(views);
        assertEquals(4, summary.total());
        assertEquals(1, summary.running());
        assertEquals(1, summary.stopped());
        assertEquals(1, summary.error());
        assertEquals(1, summary.unknown());
        // 分项之和必须等于总数，否则统计卡会出现「1+1+1 ≠ 4」
        assertEquals(summary.total(),
                summary.running() + summary.stopped() + summary.error() + summary.unknown());
        assertEquals(6, summary.plugins());
        assertEquals(4, summary.names().size());
    }

    @Test
    @DisplayName("空列表统计全 0，不抛异常")
    void summarizeEmpty() {
        ServerNodeSummary summary = ServerNodePager.summarize(null);
        assertEquals(0, summary.total());
        assertEquals(0, summary.plugins());
        assertTrue(summary.names().isEmpty());
    }
}
