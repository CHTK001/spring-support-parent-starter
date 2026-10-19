package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「已落库但运行态操作失败」时必须把载荷一并回传。
 *
 * <p>这是一个真实缺陷的回归测试。服务管理里创建/热更新节点有同一个结构性特点：
 * <b>先把定义写进库，再去拉起运行态</b>。第二步失败时库里已经多了一条记录，
 * 但响应里 {@code data} 却是 {@code null} —— 于是调用方拿不到 id。</p>
 *
 * <p>后果很具体：任何「按返回 id 回滚」的自动化流程都会漏掉这条记录，
 * 留下一条启动不了的僵尸节点，只能靠人按名字去列表里猜着删。
 * 实测中就是这样漏掉过一次，清理时得靠名称前缀兜底才把它删掉。</p>
 *
 * <p>这里的判定只依赖 {@link ReturnResult} 的载荷语义，不依赖 Spring 上下文：
 * 「失败时有没有把已落库实体的 id 带出来」是一个纯数据契约，
 * 为它拉起整个应用上下文既慢又脆。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("失败但已落库时回传载荷")
class ServerManagePartialFailurePayloadTest {

    @Test
    @DisplayName("error(code, msg, data) 必须保留 data 且 success 仍为 false")
    void errorWithPayloadKeepsDataButStillFails() {
        ReturnResult<ServerNodeView> result = ReturnResult.error(
                "BIZ_NODE_START_FAILED", "节点已保存但启动失败: 端口被占用", view(7001L));

        assertFalse(result.isSuccess(),
                "带载荷不能让 success 变 true —— 否则只看 success 的调用方会把失败当成功");
        assertNotNull(result.getData(), "失败时必须回传已落库的节点，调用方才能按 id 回滚");
        assertEquals(7001L, result.getData().id(), "载荷里的 id 必须可用于回滚");
        assertTrue(String.valueOf(result.getMsg()).contains("节点已保存但启动失败"),
                "失败原因不能因为带了载荷就丢掉，实际=" + result.getMsg());
    }

    @Test
    @DisplayName("两参 error(code, msg) 不得携带载荷 —— 这是被删掉的歧义重载的陷阱本身")
    void twoArgErrorIsCodeAndMsgNotPayload() {
        // 曾经存在 error(String msg, T data)：当第二个实参恰好是 String 时，
        // 它与 error(String code, String msg) 都是 (String,String) 完美匹配，
        // 编译器会静默选中先声明的那个 —— 错误信息被当成业务码，载荷被丢弃。
        // 现在只保留三参形式，这里的断言锁住「两参 == (code, msg)」这一语义，
        // 防止有人日后再加回一个 (String, String) 的携带载荷重载。
        ReturnResult<ServerNodeView> result = ReturnResult.error("BIZ_NODE_START_FAILED", "节点已保存但启动失败");

        assertFalse(result.isSuccess());
        assertEquals("BIZ_NODE_START_FAILED", result.getCode(), "两参的第一位是业务码，不是错误信息");
        assertEquals("节点已保存但启动失败", result.getMsg(), "两参的第二位是错误信息");
        assertNull(result.getData(), "两参形式没有载荷位，data 必须为 null —— "
                + "若这里非 null，说明有 (String,String) 携带载荷的重载被加回来了");
    }

    @Test
    @DisplayName("原有 error(msg) 行为不变：data 仍为 null（不能因新重载而改变老调用方）")
    void plainErrorStillHasNoPayload() {
        ReturnResult<ServerNodeView> result = ReturnResult.error("节点不存在: 1");

        assertFalse(result.isSuccess());
        assertNull(result.getData(), "不带载荷的重载必须保持 data=null，否则会误导成「已落库」");
    }

    /**
     * 构造节点视图。
     *
     * @param id 节点 id
     * @return 节点视图
     */
    private static ServerNodeView view(Long id) {
        return new ServerNodeView(id, "acc-partial-" + id, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}