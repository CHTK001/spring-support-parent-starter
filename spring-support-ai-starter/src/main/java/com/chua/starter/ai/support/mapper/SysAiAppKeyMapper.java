package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI appKey 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiAppKeyMapper extends BaseMapper<SysAiAppKey> {

    /**
     * 按分组 标识 查询 appKey 列表。
     *
     * @param groupId 分组 标识
     * @return AppKey 列表
     * @param @Param("groupId" @参数("群体标识"
     */
    List<SysAiAppKey> selectByGroupId(@Param("groupId") Integer groupId);

    /**
     * 按 appKey 查询。
     *
     * @param appKey appKey
     * @return AppKey 实体，不存在时返回 空
     * @param @Param("appKey" @参数("app键"
     */
    SysAiAppKey selectByAppKey(@Param("appKey") String appKey);

    /**
     * 原子自增使用次数，避免并发下丢失更新。
     *
     * <p>对应 SQL：{@code SET sys_ai_app_key_use_count = COALESCE(sys_ai_app_key_use_count, 0) + 1}</p>
     *
     * @param appKey appKey 值
     * @param @Param("appKey" @参数("app键"
     */
    void updateUseCount(@Param("appKey") String appKey);

    /**
     * 原子累加 令牌 消耗量，避免并发下丢失更新。
     *
     * <p>对应 SQL：{@code SET sys_ai_app_key_total_tokens = COALESCE(sys_ai_app_key_total_tokens, 0) + #{tokens}}</p>
     *
     * @param appKey  appKey 值
     * @param tokens  本次消耗的 令牌 数
     * @param @Param("appKey" @参数("app键"
     */
    void addTokenQuota(@Param("appKey") String appKey, @Param("tokens") long tokens);

    /**
     * 原子扣费：余额减去金额、累计费用加上金额。
     *
     * <p>为什么必须一条 SQL 完成：先查余额再写回余额，是两个并发请求各自读到同一个
     * 「变动前余额」，后写的那个把先写的扣减整笔覆盖掉 —— 用户并发打两句话只扣了一次钱。
     * 把「是否可扣」也放进 {@code WHERE}，判断与写入就在同一行锁里完成。</p>
     *
     * <p>返回 0 表示没有扣成：Key 不存在 / 已删除 / 余额已为负且未开透支。</p>
     *
     * @param appKey appKey 值
     * @param amount 扣减金额（正数）
     * @return 受影响行数（1 = 扣费成功）
     */
    int deductBalance(@Param("appKey") String appKey, @Param("amount") BigDecimal amount);

    /**
     * 原子充值：余额加上金额。
     *
     * <p>与 {@link #deductBalance} 成对：先查余额再整字段写回，会让并发的充值/扣费
     * 互相覆盖（只留下一笔的效果），一条 SQL 自增才能保证每笔都在前一笔之后结算。</p>
     *
     * <p>返回 0 表示没有充成：Key 不存在 / 已删除。</p>
     *
     * @param appKey appKey 值
     * @param amount 充值金额（正数）
     * @return 受影响行数（1 = 充值成功）
     */
    int rechargeBalance(@Param("appKey") String appKey, @Param("amount") BigDecimal amount);

    /**
     * 原子充值（按主键）：余额加上金额。
     *
     * <p>控制台按 appKey 主键操作，与运行时按 appKey 值的链路共用同一套原子语义。</p>
     *
     * @param id     主键
     * @param amount 充值金额（正数）
     * @return 受影响行数（1 = 充值成功）
     */
    int rechargeBalanceById(@Param("id") Integer id, @Param("amount") BigDecimal amount);

    /**
     * 原子扣费（按主键）：余额减少、累计费用增加。
     *
     * <p>{@code WHERE} 里的「余额不为负或允许透支」与扣减在同一行锁内完成，
     * 返回 0 即表示没扣成。</p>
     *
     * @param id     主键
     * @param amount 扣减金额（正数）
     * @return 受影响行数（1 = 扣费成功）
     */
    int deductBalanceById(@Param("id") Integer id, @Param("amount") BigDecimal amount);
}
