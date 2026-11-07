package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.vo.HostArchiveImportVO;
import com.chua.starter.server.support.model.vo.HostArchiveVO;

/**
 * 主机资产归档服务：把「主机 + 登录身份 + 绑定关系」导出成一份 JSON，或从该 JSON 还原。
 *
 * <p>与 CSV 导出的分工：CSV 只给「看」，归档 JSON 给「搬」。归档里带口令密文，
 * 所以导出接口必须受登录态保护，且 {@code credential-key} 不同的环境之间不能互换。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
public interface HostArchiveService {

    /**
     * 导出全部主机资产。
     *
     * @return 归档内容（口令为密文）
     */
    HostArchiveVO export();

    /**
     * 导入归档内容（幂等：主机按地址+端口、身份按名称去重）。
     *
     * @param archive 归档内容
     * @return 导入结果
     */
    HostArchiveImportVO importArchive(HostArchiveVO archive);
}
