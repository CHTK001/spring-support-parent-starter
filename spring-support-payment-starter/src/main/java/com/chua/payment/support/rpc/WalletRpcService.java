package com.chua.payment.support.rpc;

import com.chua.payment.support.dto.WalletPayRequest;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.dto.WalletRefundRequest;
import com.chua.payment.support.dto.WalletTransferOpDTO;
import com.chua.payment.support.vo.WalletAccountVO;

/**
 * 钱包 RPC 门面（服务/客户端双模式的服务端契约）。
 *
 * <p>把 {@code WalletAccountService} 中适合跨应用消费的核心能力收敛为稳定契约，
 * 经 {@code spring-support-rpc-starter}（Dubbo）暴露。钱包操作均以
 * {@code merchantId + userId} 定位账户，入参 / 出参全部是 {@code Serializable}。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // 消费者侧（其它服务端 / SDK）
 * public class MembershipService {
 *
 *     @RpcResource(version = "1.0.0", degrade = "springbean")
 *     private WalletRpcService walletRpcService;   // RPC 代理；Dubbo 不可用时降级到本地 Bean
 *
 *     public WalletAccountVO balance(Long merchantId, Long userId) {
 *         return walletRpcService.getAccount(merchantId, userId);
 *     }
 * }
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.51
 * @see com.chua.common.support.network.rpc.RpcService
 * @see com.chua.common.support.network.rpc.RpcResource
 */
public interface WalletRpcService {

    /**
     * 查询钱包账户（可用 / 冻结余额与状态）。
     *
     * @param merchantId 商户标识
     * @param userId     用户标识
     * @return 钱包账户
     */
    WalletAccountVO getAccount(Long merchantId, Long userId);

    /**
     * 确保钱包账户存在，不存在则初始化（幂等）。
     *
     * @param merchantId 商户标识
     * @param userId     用户标识
     */
    void ensureAccount(Long merchantId, Long userId);

    /**
     * 钱包充值（入账）。
     *
     * @param dto 充值参数（金额 / 充值单号 / 通知地址等）
     * @return 充值后的钱包账户
     */
    WalletAccountVO recharge(WalletRechargeDTO dto);

    /**
     * 钱包支付（扣减可用余额，余额不足报错）。
     *
     * @param request 支付参数（金额 / 订单号等）
     * @return 支付后的钱包账户
     */
    WalletAccountVO pay(WalletPayRequest request);

    /**
     * 钱包退款（回补可用余额）。
     *
     * @param request 退款参数（金额 / 退款单号等）
     * @return 退款后的钱包账户
     */
    WalletAccountVO refund(WalletRefundRequest request);

    /**
     * 同商户内两个用户之间转账（转出扣减、转入入账，单号防重）。
     *
     * @param dto 转账参数（转出 / 转入用户与金额、转账单号等）
     */
    void transfer(WalletTransferOpDTO dto);
}
