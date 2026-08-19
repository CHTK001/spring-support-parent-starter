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

    /**
     * 创建 TreeNode 实例
     * @param data data
     * @param Object Object
     * @param Object Object
     */
    public TreeNode(T data, Object id, Object parentId) {
        this.data = data;
        this.id = id;
        this.parentId = parentId;
    }

    /** 获取Data */
    public T getData() {
        return data;
    }

    /** 设置Data */
    public void setData(T data) {
        this.data = data;
    }

    /** 获取Id */
    public Object getId() {
        return id;
    }

    /** 设置Id */
    public void setId(Object id) {
        this.id = id;
    }

    /** 获取ParentId */
    public Object getParentId() {
        return parentId;
    }

    /** 设置ParentId */
    public void setParentId(Object parentId) {
        this.parentId = parentId;
    }

    /** 获取Children */
    public List<TreeNode<T>> getChildren() {
        return children;
    }

    /** 设置Children */
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
