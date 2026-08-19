package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiManufacturer;

import java.util.List;

/**
 * AI 厂商门面服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiManufacturerFacadeService {

    /**
     * 查询全部厂商。
     *
     * @return 厂商列表
     */
    List<SysAiManufacturer> list();

    /**
     * 新增厂商。
     *
     * @param manufacturer 厂商实体
     * @return 保存后的厂商
     */
    SysAiManufacturer save(SysAiManufacturer manufacturer);

    /**
     * 更新厂商。
     *
     * @param manufacturer 厂商实体
     * @return 是否成功
     */
    boolean update(SysAiManufacturer manufacturer);

    /**
     * 删除厂商。
     *
     * @param id 厂商 ID
     * @return 是否成功
     */
    boolean delete(Integer id);

    /**
     * 按 ID 删除厂商。
     *
     * @param id 厂商 ID
     * @return 是否成功
     */
    boolean deleteById(Integer id);

    /**
     * 从厂商 API 直拉模型列表，同步到模型表（先删后插）。
     *
     * @param code 厂商编码
     * @return 同步的模型数量
     */
    int syncModels(String code);
}
