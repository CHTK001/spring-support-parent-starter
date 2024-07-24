package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.chain.filter.FileStorageChainFilter;
import com.chua.common.support.constant.Action;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.FileStorage;
import com.chua.starter.monitor.server.entity.FileStorageProtocol;

/**
 *
 *
 * @since 2024/7/22 
 * @author CH
 */
public interface FileStorageProtocolService extends IService<FileStorageProtocol>{

    /**
     * 启动监控代理。
     * <p>此方法用于启动传入的监控代理实例。如果启动成功，返回true；否则返回false。</p>
     *
     * @param fileStorageProtocol 监控代理实例，不能为空。
     * @return boolean 返回启动结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> start(FileStorageProtocol fileStorageProtocol);

    /**
     * 停止监控代理。
     * <p>此方法用于停止传入的监控代理实例。如果停止成功，返回true；否则返回false。</p>
     *
     * @param fileStorageProtocol 监控代理实例，不能为空。
     * @return boolean 返回停止结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> stop(FileStorageProtocol fileStorageProtocol);

    /**
     * 刷新监控代理。
     * <p>此方法用于刷新传入的监控代理实例。</p>
     *
     * @param fileStorageProtocolId 监控代理实例的ID，不能为空。
     */
    void refresh(Integer fileStorageProtocolId);

    /**
     * 更新监控代理。
     * <p>此方法用于更新传入的监控代理实例。如果更新成功，返回true；否则返回false。</p>
     *
     * @param t 监控代理实例，不能为空。
     * @return boolean 返回更新结果，成功为true，失败为false。
     */
    Boolean updateFor(FileStorageProtocol t);
    /**
     * 更新监控代理。
     * <p>此方法用于更新传入的监控代理实例。如果更新成功，返回true；否则返回false。</p>
     *
     * @param t 监控代理实例，不能为空。
     * @param action 操作
     * @return boolean 返回更新结果，成功为true，失败为false。
     */
    Boolean updateFor(FileStorage t, Action action);

    /**
     * 获取工厂
     * @param fileStorage 文件存储
     * @return 工厂
     */
    FileStorageChainFilter.FileStorageFactory getFactory(FileStorage fileStorage);
}
