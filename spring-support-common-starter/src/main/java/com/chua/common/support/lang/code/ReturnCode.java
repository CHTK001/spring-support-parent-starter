package com.chua.common.support.lang.code;

/**
 * 返回状态码类，定义系统常用的错误码和状态码
 *
 * @author CH
 */
public class ReturnCode {
    /**
     * 状态码数值
     */
    private final int code;

    /**
     * 状态码对应的消息描述
     */
    private final String msg;

    /**
     * 构造一个状态码实例
     *
     * @param code 状态码数值
     * @param msg  状态码对应的消息描述
     */
    public ReturnCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取状态码数值
     *
     * @return 状态码
     */
    public int getCode() { return code; }

    /**
     * 获取状态码对应的消息描述
     *
     * @return 消息描述
     */
    public String getMsg() { return msg; }

    /**
     * 请求参数错误（400）
     */
    public static final ReturnCode REQUEST_PARAM_ERROR = new ReturnCode(400, "");
    public static final ReturnCode REQUEST_PARAM_EMPTY = new ReturnCode(401, "");
    public static final ReturnCode REQUEST_PARAM_FORMAT_ERROR = new ReturnCode(402, "");
    public static final ReturnCode REQUEST_PARAM_OUT_OF_RANGE = new ReturnCode(403, "");
    public static final ReturnCode RESOURCE_NOT_FOUND = new ReturnCode(404, "");
    public static final ReturnCode FILE_SIZE_EXCEEDED = new ReturnCode(413, "");
    public static final ReturnCode DATA_CONFLICT = new ReturnCode(409, "");
    public static final ReturnCode DATA_ALREADY_EXISTS = new ReturnCode(410, "");
    public static final ReturnCode DATABASE_ERROR = new ReturnCode(500, "");
    public static final ReturnCode BUSINESS_ERROR = new ReturnCode(501, "");
    public static final ReturnCode SYSTEM_EXECUTION_ERROR = new ReturnCode(502, "");
    public static final ReturnCode SYSTEM_EXECUTION_TIMEOUT = new ReturnCode(503, "");
    public static final ReturnCode SYSTEM_SERVER_OTHER_ERROR = new ReturnCode(504, "");
    public static final ReturnCode SERVICE_UNAVAILABLE = new ReturnCode(505, "");
    public static final ReturnCode THIRD_PARTY_ERROR = new ReturnCode(506, "");
    public static final ReturnCode USER_NOT_LOGIN = new ReturnCode(507, "");
    public static final ReturnCode USER_NOT_AUTHORIZED = new ReturnCode(508, "");
    public static final ReturnCode OPERATION_NOT_ALLOWED = new ReturnCode(509, "");
    /**
     * 系统服务器业务错误（510）
     */
    public static final ReturnCode SYSTEM_SERVER_BUSINESS_ERROR = new ReturnCode(510, "");

    /**
     * 根据异常对象获取对应的返回状态码
     * <p>当前实现固定返回 {@link #SYSTEM_SERVER_OTHER_ERROR}，子类可覆写此方法实现精细化映射。</p>
     *
     * @param throwable 异常对象
     * @return 对应的 ReturnCode 实例
     */
    public static ReturnCode fromThrowable(Throwable throwable) {
        return SYSTEM_SERVER_OTHER_ERROR;
    }
}
