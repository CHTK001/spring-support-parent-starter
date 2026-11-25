package com.chua.payment.support.rpc;

import com.chua.common.support.network.rpc.RpcService;
import com.chua.payment.support.dto.WalletPayRequest;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.dto.WalletRefundRequest;
import com.chua.payment.support.dto.WalletTransferOpDTO;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.vo.WalletAccountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 钱包 RPC 提供者：把 {@link WalletAccountService} 的核心能力委托暴露为
 * {@link WalletRpcService} 契约。
 *
 * <p>注册与发现规约：<br>
 * 1. 仅在 {@code plugin.rpc.enable=true} 时对外注册（默认关闭）；<br>
 * 2. 扫描包路径 {@code plugin.rpc.scan}（缺省扫描应用入口类所在包，需覆盖
 * {@code com.chua.payment.support.rpc}）；<br>
 * 3. 消费者侧用 {@code @RpcResource(version = "1.0.0", degrade = "springbean")} 注入，
 * RPC 层（Dubbo）不可用时自动降级到本应用的本地 Bean。</p>
 *
 * <p>本类只做参数透传与结果包装，不实现任何钱包业务规则；业务归属
 * {@code WalletAccountService} 及其下游（R1：可部署宿主不重复实现 starter 业务）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
@RpcService(interfaceClass = WalletRpcService.class, version = "1.0.0")
public class WalletRpcServiceImpl implements WalletRpcService {

    private final WalletAccountService walletAccountService;

    @Override
    public WalletAccountVO getAccount(Long merchantId, Long userId) {
        return walletAccountService.getAccount(merchantId, userId);
    }

    @Override
    public void ensureAccount(Long merchantId, Long userId) {
        walletAccountService.ensureAccount(merchantId, userId);
    }

    @Override
    public WalletAccountVO recharge(WalletRechargeDTO dto) {
        return walletAccountService.recharge(dto);
    }

    @Override
    public WalletAccountVO pay(WalletPayRequest request) {
        return walletAccountService.pay(request);
    }

    @Override
    public WalletAccountVO refund(WalletRefundRequest request) {
        return walletAccountService.refund(request);
    }

    @Override
    public void transfer(WalletTransferOpDTO dto) {
        walletAccountService.transfer(dto);
    }
}
