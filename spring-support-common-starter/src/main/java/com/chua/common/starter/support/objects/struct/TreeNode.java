package com.chua.common.starter.support.objects.struct;

import java.util.ArrayList;
import java.util.List;

/**
 * 树形节点
 *
 * @author CH
 */
public class TreeNode<T> {

    /** 节点数据 */
    private T data;

    /** 节点标识 */
    private Object id;

    /** 父节点标识 */
    private Object parentId;

    /** 子节点集合 */
    private List<TreeNode<T>> children = new ArrayList<>();

    public TreeNode(T data, Object id, Object parentId) {
        this.data = data;
        this.id = id;
        this.parentId = parentId;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public Object getParentId() {
        return parentId;
    }

    public void setParentId(Object parentId) {
        this.parentId = parentId;
    }

    public List<TreeNode<T>> getChildren() {
        return children;
    }

    public void setChildren(List<TreeNode<T>> children) {
        this.children = children;
    }

    /**
     * 添加子节点
     *
     * @param child 子节点
     */
    public void addChild(TreeNode<T> child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    /**
     * 判断是否为叶子节点
     *
     * @return 是否叶子节点
     */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }
}
