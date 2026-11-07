package com.chua.starter.ai.support.configuration.client;

import java.util.List;

/**
 * 生效对话模型解析契约：全项目唯一的「该用哪个模型对话」解析入口。
 *
 * <p>优先级（第一个可用者胜出）：</p>
 * <ol>
 *   <li>调用方显式指定的模型（例如连接器/Bot 绑定自己配的模型）</li>
 *   <li>配置中心 Bot 分组默认：{@code bot/default_model}（该用户的覆盖优先，其次全局）</li>
 *   <li>配置中心「对话路由」的默认对话模型：{@code runtime/default_model}
 *       （该用户的覆盖优先，其次全局；兼容旧键 {@code models/defaultChatModelId}、
 *       {@code runtime/default_chat_model}）</li>
 * </ol>
 *
 * <p>即连接器链路的兜底顺序为 <b>连接器模型 &gt; Bot 模型 &gt; 默认模型</b>。</p>
 *
 * <p><b>刻意不做的两件事：</b></p>
 * <ul>
 *   <li><b>不写死任何模型名。</b>以前多处把兜底写成字面量 {@code agnes-2.5-flash} / {@code gpt-4o}，
 *       现场没启用该模型时整条链路必然失败，且报错指名一个用户从未配过的模型。</li>
 *   <li><b>不自动挑模型。</b>各级配置都不可用时返回 {@code null}，由调用方给出明确、可执行的错误提示。</li>
 * </ul>
 *
 * <p>读取一律直连数据库（不走 Redis 缓存）：模型解析错一次就整条链路失败，
 * 缓存里残留的旧模型名会让报错与库里的配置完全对不上。</p>
 *
 * <p>其它模块（如 datasource-hub 的数据库控制台）只依赖本接口；
 * 实现由 {@code DefaultEffectiveChatModelResolver} 提供。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface EffectiveChatModelResolver {

    /**
     * 解析生效对话模型。
     *
     * @param explicitModel 调用方显式指定的模型，可为空
     * @return 生效模型名；各级配置均不可用时返回 {@code null}
     */
    String resolve(String explicitModel);

    /**
     * 解析候选模型链（已去重、已剔除「模型管理里不存在或未启用」的项）。
     *
     * @param explicitModel 调用方显式指定的模型，可为空
     * @return 按优先级排列的可用模型名列表，可能为空
     */
    List<String> candidates(String explicitModel);

    /**
     * 判断模型是否已在「模型管理」中配置且启用（多种写法都试）。
     *
     * @param modelName 模型名，可为空
     * @return 任一种写法命中已启用模型时为 true
     */
    boolean isAvailable(String modelName);
}
