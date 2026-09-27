package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerGroup;
import com.chua.starter.server.support.model.vo.ServerGroupNodeVO;

import java.util.List;

/**
 * 服务器分组服务
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ServerGroupService {

    /**
     * 新增分组
     *
     * @param group 分组
     * @return 结果
     */
    ReturnResult<MsServerGroup> create(MsServerGroup group);

    /**
     * 更新分组
     *
     * @param id    主键
     * @param group 分组
     * @return 结果
     */
    ReturnResult<MsServerGroup> update(Long id, MsServerGroup group);

    /**
     * 删除分组（存在子分组或主机时拒绝）
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 查询全部分组（平铺，前端组装树）
     *
     * @return 分组列表
     */
    ReturnResult<List<MsServerGroup>> list();

    /**
     * 查询分组树（按父键组装，根节点父键为 null 或 0）
     *
     * @return 根节点列表
     */
    ReturnResult<List<ServerGroupNodeVO>> tree();
}
