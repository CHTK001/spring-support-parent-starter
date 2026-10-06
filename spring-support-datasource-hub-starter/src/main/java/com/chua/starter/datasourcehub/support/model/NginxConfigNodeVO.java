package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Nginx 配置语法树节点（面向前端）。
 *
 * <p>既是「文件模式」下代码编辑器的目录大纲，也是「UI 模式」下表单渲染的数据源。
 * 前端拿到的树是原配置的<strong>无损投影</strong>：未改动的节点仍带 {@link #originalText}，
 * 因此 UI 模式提交时可以只回传改动部分。</p>
 *
 * @param id            节点在本棵子树内的稳定标识（路径式，形如 {@code 0/2/1}）
 * @param parentId      父节点标识，根为 null
 * @param name          区块名或指令名
 * @param headArg       区块头部参数，指令为 null
 * @param label         展示标题
 * @param block         是否区块
 * @param value         叶子指令的参数文本
 * @param scope         该节点所属作用域（区块节点有效）
 * @param startLine     原始起始行号
 * @param endLine       原始结束行号
 * @param originalText  原始文本切片
 * @param dirty         是否已被 UI 改动
 * @param removed       是否已被 UI 删除
 * @param known         指令名是否有元数据登记
 * @param meta          指令元数据，区块节点为 null
 * @param warnings      作用于本节点的解析告警
 * @param children      子节点
 * @author CH
 * @since 4.0.0.42
 */
public record NginxConfigNodeVO(
        String id,
        String parentId,
        String name,
        String headArg,
        String label,
        boolean block,
        String value,
        String scope,
        Integer startLine,
        Integer endLine,
        String originalText,
        boolean dirty,
        boolean removed,
        boolean known,
        NginxDirectiveMetaVO meta,
        List<String> warnings,
        List<NginxConfigNodeVO> children
) {
    /**
     * 规范构造器：本节点告警与子节点做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。告警由解析器就地收集、子节点由递归
     * 转 VO 就地组装，两者恒不为 null 且元素恒不为 null，故可使用拒绝 null 的
     * {@link List#copyOf}。</p>
     *
     * @param warnings 作用于本节点的解析告警
     * @param children 子节点
     */
    public NginxConfigNodeVO {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings 不能为 null"));
        children = List.copyOf(Objects.requireNonNull(children, "children 不能为 null"));
    }
}
