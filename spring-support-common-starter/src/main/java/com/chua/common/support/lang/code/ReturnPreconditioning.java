package com.chua.common.support.lang.code;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.ToString;

import com.chua.starter.common.support.http.ContentDisposition;
import com.chua.starter.common.support.http.ContentType;

/**
* 返回前置条件
*
* @author CH
* @since 4.0.0
 */
@Getter
@ToString
@SuppressWarnings("ALL")
public class ReturnPreconditioning<T> {

    /**
    * 非空判断条件：对象不为 空 时满足条件
    */
    public static final Predicate<Object> NOT_NULL = data -> null != data;

    /**
    * 布尔判断条件：值为 true 时满足条件
    */
    public static final Predicate<Boolean> BOOLEAN = data -> null != data && data;

    /**
    * 数值判断条件：数值不为 空 且大于 0 时满足条件
    */
    public static final Predicate<Number> NUMBER = data -> null != data && data.intValue() > 0;

    /**
    * 待判断的数据
    */
    private final T data;

    /**
    * 判断条件
    */
    private Predicate<T> condition;

    /**
    * 成功时的提示消息
    */
    private String successMessage;

    /**
    * 失败时的错误消息，默认为 "服务器异常, 请稍后重试!"
    */
    private String errorMessage = "服务器异常, 请稍后重试!";

    /**
    * 导致的异常信息
    */
    private Throwable throwable;

    /**
    * 错误码
    */
    private ResultCode errorCode;

    /**
    * 构造前置条件实例
    *
    * @param data      待判断的数据
    * @param condition 判断条件
    */
    protected ReturnPreconditioning(T data, Predicate<T> condition) {
        this.data = data;
        this.condition = condition;
    }

    /**
    * 根据给定的数据创建前置条件实例，自动推断合适的默认判断条件
    * <ul>
    *   <li>Boolean 类型使用 {@link #BOOLEAN}</li>
    *   <li>Number 类型使用 {@link #NUMBER}</li>
    *   <li>其他类型使用 {@link #NOT_NULL}</li>
    * </ul>
    *
    * @param <T>  数据类型
    * @param data 待判断的数据
    * @return ReturnPreconditioning 实例
    */
    @SuppressWarnings("unchecked")
    public static <T> ReturnPreconditioning<T> of(T data) {
        Predicate<T> condition;
        if (data instanceof Boolean) {
            condition = (Predicate<T>) BOOLEAN;
        } else if (data instanceof Number) {
            condition = (Predicate<T>) NUMBER;
        } else {
            condition = (Predicate<T>) NOT_NULL;
        }
        return new ReturnPreconditioning<>(data, condition);
    }

    /**
    * 根据数据提供者创建前置条件实例，延迟获取数据
    *
    * @param <T>      数据类型
    * @param supplier 数据提供者
    * @return ReturnPreconditioning 实例
    */
    public static <T> ReturnPreconditioning<T> of(Supplier<T> supplier) {
        return of(null == supplier ? null : supplier.get());
    }

    /**
    * 根据可调用任务创建前置条件实例，执行任务并获取数据
    *
    * @param <T>      数据类型
    * @param callable 可调用任务
    * @return ReturnPreconditioning 实例
    * @throws RuntimeException 如果 callable 执行抛出异常
    */
    public static <T> ReturnPreconditioning<T> ofCallable(Callable<T> callable) {
        try {
            return of(null == callable ? null : callable.call());
        } catch (Exception e) {
            /**
            * runtime异常。
            * @param e e
            * @return runtime异常的结果
            */
            throw new RuntimeException(e);
        }
    }

    /**
    * 设置自定义判断条件
    *
    * @param condition 判断条件
    * @return 当前实例，支持链式调用
    */
    public ReturnPreconditioning<T> withCondition(Predicate<T> condition) {
        this.condition = condition;
        return this;
    }

    /**
    * 设置成功时的提示消息
    *
    * @param message 成功消息
    * @return 当前实例，支持链式调用
    */
    public ReturnPreconditioning<T> withSuccessMessage(String message) {
        this.successMessage = message;
        return this;
    }

    /**
    * 设置失败时的错误消息
    *
    * @param message 错误消息
    * @return 当前实例，支持链式调用
    */
    public ReturnPreconditioning<T> withErrorMessage(String message) {
        this.errorMessage = message;
        return this;
    }

    /**
    * 使用转换函数将当前数据转换为新的类型，生成新的前置条件
    *
    * @param <R>      目标数据类型
    * @param function 转换函数
    * @return 新的 申报preconditioning 实例
    */
    public <R> ReturnPreconditioning<R> withConverter(Function<T, R> function) {
        return of(function.apply(data));
    }

    /**
    * 设置导致的异常信息
    *
    * @param throwable 异常对象
    * @return 当前实例，支持链式调用
    */
    public ReturnPreconditioning<T> withThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    /**
    * 设置错误码
    *
    * @param errorCode 错误码
    * @return 当前实例，支持链式调用
    */
    public ReturnPreconditioning<T> withErrorCode(ResultCode errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    /**
    * 根据条件判断结果执行不同的回调函数
    *
    * @param <R>       返回值类型
    * @param onSuccess 条件满足时执行的回调
    * @param onFailure 条件不满足时执行的回调
    * @return 执行对应回调后的结果
    */
    public <R> R when(Function<T, R> onSuccess, Supplier<R> onFailure) {
        if (isSuccessful()) {
            return onSuccess.apply(data);
        }
        return onFailure.get();
    }

    /**
    * 根据条件判断结果返回统一的 申报结果
    * <p>条件满足时返回成功结果（包含转换后的数据），否则返回错误结果。</p>
    *
    * @param <R>       返回值类型
    * @param onSuccess 条件满足时对数据的转换函数
    * @return ReturnResult 实例
    */
    public <R> ReturnResult<R> then(Function<T, R> onSuccess) {
        if (isSuccessful()) {
            return ReturnResult.ok(onSuccess.apply(data), successMessage);
        }
        return ReturnResult.error(errorMessage);
    }

    /**
    * 条件不满足时返回备选值
    *
    * @param other 备选值
    * @return 满足条件时返回原数据，否则返回备选值
    */
    public T otherwise(T other) {
        return isSuccessful() ? data : other;
    }

    /**
    * 条件不满足时通过提供者获取备选值
    *
    * @param supplier 备选值的提供者
    * @return 满足条件时返回原数据，否则从 供应商 获取
    */
    public T otherwiseGet(Supplier<T> supplier) {
        return isSuccessful() ? data : supplier.get();
    }

    /**
    * 判断当前条件是否满足
    *
    * @return 条件满足返回 true，否则 false
    */
    public boolean isSuccessful() {
        return null != condition && condition.test(data);
    }

    /**
    * 将前置条件结果转换为统一的 申报结果 响应
    * <p>成功时包含数据和成功消息；失败时根据异常、错误码或默认错误码构造错误响应。</p>
    *
    * @return ReturnResult 实例
    */
    public ReturnResult<T> asResult() {
        if (isSuccessful()) {
            return ReturnResult.ok(data, successMessage);
        }
        if (null != throwable) {
            ReturnCode returnCode = ReturnCode.fromThrowable(throwable);
            return ReturnResult.error(returnCode.getCode(), errorMessage);
        }
        if (null != errorCode) {
            return ReturnResult.error(errorCode.getCode(), errorMessage);
        }
        return ReturnResult.error(ReturnCode.SYSTEM_SERVER_BUSINESS_ERROR.getCode(), errorMessage);
    }

    /**
    * {@link #asResult()} 的别名方法
    *
    * @return ReturnResult 实例
    */
    public ReturnResult<T> toResult() {
        return asResult();
    }

    /**
    * 获取内容类型，当前实现固定返回 空
    *
    * @return 内容类型，始终为 空
    */
    public ContentType getContentType() {
        return null;
    }

    /**
    * 获取内容分发描述，当前实现固定返回 空
    *
    * @return 内容分发描述，始终为 空
    */
    public ContentDisposition getContentDisposition() {
        return null;
    }

    /**
    * 判断是否需要解包
    *
    * @return 始终返回 false
    */
    public boolean hasUnpack() {
        return false;
    }

    /**
    * 判断是否为异步操作
    *
    * @return 始终返回 false
    */
    public boolean isAsync() {
        return false;
    }

    /**
    * 获取超时时间（毫秒）
    *
    * @return 始终返回 0，表示不超时
    */
    public long getTimeoutMillis() {
        return 0;
    }
}
