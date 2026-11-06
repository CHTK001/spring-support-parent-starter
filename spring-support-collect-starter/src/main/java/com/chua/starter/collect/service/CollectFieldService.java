package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.entity.CollectField;

import java.util.Collection;
import java.util.List;

/**
 * 采集结果字段服务。
 *
 * <p>字段定义决定结果表格的列与顺序，采集落库时由落库通道自动登记，
 * 也允许运维在设计器里改名、调序或隐藏列。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectFieldService extends IService<CollectField> {

    /**
     * 按流程取字段定义，nodeId 为空时返回该流程全部字段。
     *
     * @param flowId 流程ID
     * @param nodeId 节点ID，可为空
     * @return 字段列表
     */
    List<CollectField> listFields(Integer flowId, String nodeId);

    /**
     * 覆盖保存某节点的字段定义。
     *
     * @param flowId 流程ID
     * @param nodeId 节点ID
     * @param fields 字段列表，空列表表示清空
     * @return 是否成功
     */
    boolean replaceFields(Integer flowId, String nodeId, List<CollectField> fields);

    /**
     * 登记缺失的字段，已存在的同名字段不动。
     *
     * @param flowId 流程ID
     * @param nodeId 节点ID
     * @param names  字段名集合
     * @return 新增字段数
     */
    int registerMissing(Integer flowId, String nodeId, Collection<String> names);
}
