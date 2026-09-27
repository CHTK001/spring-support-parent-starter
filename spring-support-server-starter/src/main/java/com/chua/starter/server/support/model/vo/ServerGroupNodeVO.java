package com.chua.starter.server.support.model.vo;

import java.util.List;

/**
 * 分组树节点
 * <p>
 * 由 {@code MsServerGroup} 组装的层级出参，字段名与历史 Map 载荷完全一致，
 * 前端 el-tree 直接按 {@code label=name / children=children} 消费。
 * 叶子节点的 {@code children} 为空列表而非 {@code null}。
 * </p>
 *
 * @param id       分组主键
 * @param name     分组名称
 * @param parentId 父分组主键，根节点为 0
 * @param sort     同级排序号
 * @param remark   备注
 * @param color    标签颜色
 * @param children 子节点列表，无子节点时为空列表
 * @author CH
 * @since 4.0.0.42
 */
public record ServerGroupNodeVO(
        Long id,
        String name,
        Long parentId,
        Integer sort,
        String remark,
        String color,
        List<ServerGroupNodeVO> children
) {

    /**
     * 规范构造器：子节点列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。唯一构造点传入的是递归组装出的
     * {@code ArrayList}、非空且元素非空（含叶子节点空列表），故可直接使用 {@code List.copyOf}。</p>
     *
     * @param id       分组主键
     * @param name     分组名称
     * @param parentId 父分组主键，根节点为 0
     * @param sort     同级排序号
     * @param remark   备注
     * @param color    标签颜色
     * @param children 子节点列表，无子节点时为空列表
     */
    public ServerGroupNodeVO {
        children = List.copyOf(children);
    }
}
