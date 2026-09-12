package com.chua.common.starter.support.objects.struct;

import java.util.ArrayList;
import java.util.List;

/**
* 树形节点
*
* @author CH
* @since 4.0.0
 */
public class TreeNode<T> {

    /** 节点数据 */
    /** 数据 */
    private T data;

    /** 节点标识 */
    /** 标识 */
    private Object id;

    /** 父节点标识 */
    /** 父级标识 */
    private Object parentId;

    /** 子节点集合 */
    /** Children */
    private List<TreeNode<T>> children = new ArrayList<>();

    /**
    * 树节点。
    * @param data 数据
    * @param id 标识
    * @param parentId 父标识
     */
    public TreeNode(T data, Object id, Object parentId) {
        this.data = data;
        this.id = id;
        this.parentId = parentId;
    }

    /**
    * 获取数据。
    * @return 获取数据的结果
     */
    public T getData() {
        return data;
    }

    /**
    * 设置数据。
    * @param data 数据
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
    * 获取标识。
    * @return 获取id的结果
     */
    public Object getId() {
        return id;
    }

    /**
    * 设置标识。
    * @param id 标识
     */
    public void setId(Object id) {
        this.id = id;
    }

    /**
    * 获取父标识。
    * @return 获取父id的结果
     */
    public Object getParentId() {
        return parentId;
    }

    /**
    * 设置父标识。
    * @param parentId 父标识
     */
    public void setParentId(Object parentId) {
        this.parentId = parentId;
    }

    /**
    * 获取children。
    * @return 获取children的结果
     */
    public List<TreeNode<T>> getChildren() {
        return children;
    }

    /**
    * 设置children。
    * @param children children
     */
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
