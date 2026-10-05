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
     * <p>两个字段都标为「非管理员专属」：它们决定的是「我的会话要不要出现联想面板、
     * 最多联想几条」，属于纯个人偏好，整组交给用户自管；没有用户覆盖时行为与
     * 只有全局层时完全一致。</p>
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
                new SettingItem("stream_idle_timeout_minutes", "30", "NUMBER",
                        "会话超时时间（分钟）：一次聊天流连续这么久没有任何输出就判定为超时，"
                                + "服务端结束该流并回一条超时提示，避免连接被一直挂住（1-180，0=不限制）", 3),
                /*
                 * 下面两项是故障转移模板的等待上限。必须暴露出来，否则「会话超时时间」形同虚设：
                 * 原先它们是公共库里写死的 8s（首包）/ 100s（整段完成），都比任何可用的会话超时小，
                 * 也就是说流要么在 8s 内出首包、要么在 100s 内结束，根本轮不到会话超时来收口。
                 */
                new SettingItem("stream_first_token_timeout_millis", "8000", "NUMBER",
                        "流式首包等待上限（毫秒）：超过这么久还没出首包就判定该密钥卡顿并切换（1-600000）", 4),
                new SettingItem("stream_completion_wait_seconds", "100", "NUMBER",
                        "整段回复等待上限（秒）：已开始输出后最多等这么久（1-3600）", 5)
        );
    }
}