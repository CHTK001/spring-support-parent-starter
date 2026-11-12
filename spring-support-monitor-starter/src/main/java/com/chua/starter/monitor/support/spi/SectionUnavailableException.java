package com.chua.starter.monitor.support.spi;

/**
 * 分区采集失败，但通道本身是通的。
 *
 * <p>用来区分「读不到」与「连不上」：例如读取 iptables 需要 root 权限，
 * 目标机不给权限时命令退出码非 0，但这不代表 SSH/WinRM 通道断了。
 * 上层 {@code HostProfileServiceImpl} 遇到本异常时直接把消息交给前端，
 * 不再套一层「通道不可达:」，否则用户会去查网络，而真正要改的是权限。</p>
 *
 * <p>消息必须是「可执行的动作建议」，不要只写「命令退出码非 0」。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public class SectionUnavailableException extends RuntimeException {

    /**
     * 序列号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 构造。
     *
     * @param message 面向使用者的原因与建议
     */
    public SectionUnavailableException(String message) {
        super(message);
    }
}
