package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiManufacturerKey;

import java.util.List;

/**
   * AI 厂商 API 钥匙 服务接口。
 *
 * <p>管理 {@code sys_ai_manufacturer_key} 表的 API Key，支持按厂商编码查询、增删改与状态启停。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiManufacturerKeyService {

    /**
      * 查询全部厂商 API 钥匙。
     *
     * @return API 钥匙 列表
     */
    List<SysAiManufacturerKey> list();

    /**
      * 按厂商编码查询 API 钥匙 列表。
     *
     * @param manufacturerCode 厂商编码
     * @return API 钥匙 列表
     */
    List<SysAiManufacturerKey> listByManufacturerCode(String manufacturerCode);

    /**
      * 新增厂商 API 钥匙。
     *
     * @param manufacturerKey API 钥匙 实体
     * @return 保存后的实体
     */
    SysAiManufacturerKey save(SysAiManufacturerKey manufacturerKey);

    /**
      * 更新厂商 API 钥匙。
     *
     * @param manufacturerKey API 钥匙 实体
     * @return 是否成功
     */
    boolean update(SysAiManufacturerKey manufacturerKey);

    /**
       * 按 标识 删除厂商 API 钥匙。
     *
     * @param id 主键 标识
     * @return 是否成功
     */
    boolean delete(Long id);

    /**
      * 切换 API 钥匙 启停状态。
     *
     * @param id      主键 标识
     * @param enabled 是否启用
     * @return 是否成功
     */
    boolean toggle(Long id, Boolean enabled);
}
