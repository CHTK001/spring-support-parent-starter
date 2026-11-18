package com.chua.starter.ai.support.configuration.session;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 会话级配置提供者 SPI。
 *
 * <p>定义 {@code session} 分组：<b>只放与「会话怎么用」相关的行为开关</b>，
 * 不放凭据、地址、路径，因此整组允许每个用户管理自己的值
 * （见 {@code SettingsController.USER_WRITABLE_GROUPS}）。</p>
 *
 * <p>当前只提供「AI 联想下一个任务」：开启后，会话页会在<b>输入框上方、目标条下方</b>
 * 渲染一块「AI 建议的下一步任务」面板。开关的生效口径是
 * <b>会话保留自己的原值</b>：会话首次解析时把当时的默认值钉进会话自身的
 * {@code settings.aiNextTask}，之后管理员再改默认值也不回溯改写已有会话
 * （见 {@code SessionNextTaskService}）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("session")
public class SessionGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "session";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "会话";

    /**
     * 配置分组标识。
     *
     * @return session
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 会话
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 会话分组下的全部配置项。
     *
     * <p>各项都标为「非管理员专属」：它们决定的是「我的会话要不要出现联想面板、
     * 最多联想几条、打开时先铺多少轮历史」，属于纯个人偏好，整组交给用户自管；
     * 没有用户覆盖时行为与只有全局层时完全一致。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("ai_next_task_enabled", "false", "BOOLEAN",
                        "是否允许 AI 联想下一个任务（开启后，会话输入框上方、目标条下方展示 AI 建议的下一步任务）", 1),
                new SettingItem("ai_next_task_max", "3", "NUMBER",
                        "AI 每次最多联想多少条下一步任务（1-10）", 2),
                /*
                 * 初始加载窗口按「用户轮次」计数，而不是原始消息条数：一轮问答是
                 * user + 若干 assistant（重新回答/继续会产生多条 assistant），
                 * 按消息条数截断会把某一轮的回复切掉半截，界面上就出现「有问无答」。
                 * 默认 10 轮：长会话不再一次性把整段历史铺进 DOM（那正是会话页卡死的主因），
                 * 更早的记录由「加载更早记录」入口向上翻页获取。
                 */
                new SettingItem("initial_load_turns", "10", "NUMBER",
                        "初始化加载最新的记录数量（用户轮次，1-50，默认 10）：打开会话时只加载最近这么多轮问答"
                                + "（含每轮对应的助手回复），更早的记录由向上翻页获取，避免一次性渲染整段历史把页面卡死"
                                + "（前端消费：message.api.ts 直接读该字段）", 3),
                new SettingItem("stream_idle_timeout_minutes", "5", "NUMBER",
                        "会话超时时间（分钟）：一次聊天流连续这么久没有任何输出就判定为超时，"
                                + "服务端结束该流并回一条超时提示，避免连接被一直挂住"
                                + "（1-180，0=不限制；默认 5 分钟，用量入库时超过该上限的耗时截断为上限值并标记为失败）", 4),
                /*
                 * 下面两项是故障转移模板的等待上限。必须暴露出来，否则「会话超时时间」形同虚设：
                 * 原先它们是公共库里写死的 8s（首包）/ 100s（整段完成），都比任何可用的会话超时小，
                 * 也就是说流要么在 8s 内出首包、要么在 100s 内结束，根本轮不到会话超时来收口。
                 */
                new SettingItem("stream_first_token_timeout_millis", "8000", "NUMBER",
                        "流式首包等待上限（毫秒）：超过这么久还没出首包就判定该密钥卡顿并切换（1-600000）", 5),
                new SettingItem("stream_completion_wait_seconds", "100", "NUMBER",
                        "整段回复等待上限（秒）：已开始输出后最多等这么久（1-3600）", 6),
                /*
                 * 「AI 正在思考…」占位文案（一行一句）。
                 *
                 * 默认值刻意留空串 = 用前端内置的那 18 句，**不在后端复制一份文案**：
                 * 文案是产品表现，改一句不该逼着后端一起发版；这里只开一条「用户自己覆盖」的通道。
                 * 前端拿不到该字段（未部署新版后端）时同样回落到内置默认，不会白屏。
                 */
                new SettingItem("thinking_placeholder_phrases", "", "STRING",
                        "「AI 正在思考…」占位文案（一行一句，随机轮换且不会连着重复）："
                                + "AI 答复的首字还没回来时，气泡里显示的就是这里的一句话；留空则使用内置的默认文案"
                                + "（前端消费：ThinkingPlaceholder.vue 直接读该字段）", 7),
                /*
                 * 「最后一条回复默认折叠」：用户可在会话配置里决定最新一轮 AI 回复的正文
                 * 是默认收成一行（点击展开）还是一直展开全文。开关关闭（默认 false）时，
                 * 与轨迹页等只读视图一致，全部正文常驻可见；开关打开后，每条回复的
                 * 正文默认只占一行，悬停出左侧折叠箭头，点击箭头或预览行展开全文。
                 * 前端消费：TurnItem.vue 读取该值作为 ProcessTimeline 的 collapsible-content。
                 */
                new SettingItem("collapse_last_reply", "false", "BOOLEAN",
                        "是否折叠最后一条回复的正文（开启后，最新一轮 AI 回复的正文默认只显示一行预览，"
                                + "点击左侧图标位的箭头或预览行即可展开全文；关闭则每条回复都一直展开全文）", 8),
                /*
                 * 重试与单轮超时。
                 *
                 * 与「会话超时时间」互补：那个看的是「连续多久没有输出」，是沉默流的兜底；
                 * 这里的「单轮超时」看的是「这一轮总共跑了多久」，即使一直在输出也会到点收口，
                 * 防止跑飞的长任务一直占着会话与子进程。
                 */
                new SettingItem("stream_retry_max", "2", "NUMBER",
                        "失败后自动重试次数（0-10，默认 2）：只在本轮还没有任何正文/思考/工具输出时重试，"
                                + "已经开始输出后失败会如实交给用户，避免把两段回复拼在一起", 9),
                new SettingItem("stream_retry_interval_seconds", "5", "NUMBER",
                        "重试间隔（秒，1-300，默认 5）：开启指数退避时，第 n 次重试等待 间隔×2^(n-1)", 10),
                new SettingItem("stream_retry_backoff", "false", "BOOLEAN",
                        "是否启用指数退避（关闭时每次重试都按固定间隔等待）", 11),
                new SettingItem("stream_turn_timeout_seconds", "600", "NUMBER",
                        "单轮执行超时（秒，0=不限制，默认 600）：这一轮总共跑这么久仍未完成就结束并中断执行，"
                                + "即使一直在输出也会收口；与会话超时时间（看连续沉默时长）互补", 12),
                /*
                 * 重启后自动继续：后端/opencode 重启时，会话里「用户已发问、助手还没回完」的那一轮
                 * 会停在半截。开启后由服务端在重启完成时补发「继续」，把它跑完；
                 * 终端（PTY）恢复时也同样自动输入「继续」。
                 */
                new SettingItem("auto_continue_on_restart", "true", "BOOLEAN",
                        "重启后自动继续（默认开启）：服务重启时若有未完成的回合并未收到结果，"
                                + "自动补发「继续」把它跑完；关闭时保持原样，由用户手动继续", 13)
        );
    }
}