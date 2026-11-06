package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.entity.CollectVariable;

import java.util.List;

/**
 * 采集全局变量服务。
 *
 * <p>变量在每次执行前注入根作用域，流程里以 {@code ${名称}} 引用。
 * 标记为加密的变量按密文存储，读接口只下发占位值。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectVariableService extends IService<CollectVariable> {

    /**
     * 变量列表，加密变量的值以占位符下发。
     *
     * @return 变量列表
     */
    List<CollectVariable> listMasked();

    /**
     * 新增变量。
     *
     * @param variable 变量
     * @return 保存后的变量（值已脱敏）
     */
    CollectVariable addVariable(CollectVariable variable);

    /**
     * 修改变量，值为占位符时保留原密文。
     *
     * @param variable 变量
     * @return 是否成功
     */
    boolean editVariable(CollectVariable variable);

    /**
     * 删除变量。
     *
     * @param variableId 变量ID
     * @return 是否成功
     */
    boolean deleteVariable(Integer variableId);

    /**
     * 运行时变量，已还原明文，供执行引擎注入根作用域。
     *
     * @return 名称值对列表
     */
    List<CollectParamDTO> runtimeVariables();
}
