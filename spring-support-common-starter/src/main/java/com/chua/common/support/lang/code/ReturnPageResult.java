package com.chua.common.support.lang.code;

/**
* 分页返回结果
*
* @author CH
* @since 4.0.0
 */
public class ReturnPageResult<T> {

    /**
    * 分页返回的数据承载对象
    */
    private T data;

    /**
    * 空参数构造函数，创建一个空的分页返回结果
    */
    public ReturnPageResult() {
    }

    /**
    * 指定数据构造分页返回结果
    *
    * @param data 分页数据对象
    */
    public ReturnPageResult(T data) {
        this.data = data;
    }

    /**
    * 获取分页数据
    *
    * @return 分页数据对象
    */
    public T getData() {
        return data;
    }

    /**
    * 设置分页数据
    *
    * @param data 分页数据对象
    */
    public void setData(T data) {
        this.data = data;
    }

    /**
    * 的。
    * @param data 数据
    * @return 的的结果
    */
    public static <T> ReturnPageResult<T> of(T data) {
        return new ReturnPageResult<>(data);
    }

    /**
    * 空。
    * @return 空的结果
    */
    public static <T> ReturnPageResult<T> empty() {
        return new ReturnPageResult<>(null);
    }
}
