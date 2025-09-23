package com.chua.starter.common.support.result;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.lang.exception.RemoteExecutionException;
import com.chua.common.support.unit.name.NamingCase;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.Validator;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.common.support.exception.RuntimeMessageException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.validation.ConstraintViolationException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.text.DecimalFormat;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.chua.common.support.lang.code.ReturnCode.*;

/**
 * 统一异常处理器
 * 提供详细的中文错误提示，帮助快速定位问题
 *
 * @author CH
 * @since 2023-08-01
 */
@RestControllerAdvice
@Slf4j
public class ExceptionAdvice {

    // 常用正则表达式
    private static final Pattern DATA_TOO_LONG_PATTERN = Pattern.compile("Data too long for column '([^']*)' at row");
    private static final Pattern DUPLICATE_ENTRY_PATTERN = Pattern.compile("Duplicate entry '([^']*)' for key '([^']*)'");
    private static final Pattern CONVERTER_PATTERN = Pattern.compile("\\[\"(.*?)\"]+");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##");

    /**
     * 参数绑定异常 - 表单参数验证失败
     *
     * @param e {@link BindException} 表单参数绑定异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当表单参数不符合验证规则时抛出此异常，如字段长度超出限制
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleBindException(BindException e) {
        log.warn("表单参数验证失败: {}", e.getMessage());
        String msg = e.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ReturnResult.error(REQUEST_PARAM_ERROR, "表单参数验证失败：" + msg);
    }

    /**
     * 方法参数验证异常 - JSON参数验证失败
     *
     * @param e {@link MethodArgumentNotValidException} 方法参数验证异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当JSON请求体中的字段不符合验证规则时抛出此异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("JSON参数验证失败: {}", e.getMessage());
        String msg = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ReturnResult.error(REQUEST_PARAM_ERROR, "请求参数验证失败：" + msg);
    }

    /**
     * 约束违反异常 - 数据库约束验证失败
     *
     * @param e {@link ConstraintViolationException} 约束违反异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当数据库约束（如唯一性约束）被违反时抛出此异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("数据验证约束违反: {}", e.getMessage());
        String msg = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("；"));
        return ReturnResult.error(REQUEST_PARAM_ERROR, "数据验证失败：" + msg);
    }

    /**
     * 缺少请求参数异常
     *
     * @param e {@link MissingServletRequestParameterException} 缺少请求参数异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当请求缺少必需的参数时抛出此异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少必需参数: {}", e.getParameterName());
        return ReturnResult.error(REQUEST_PARAM_ERROR,
                String.format("缺少必需参数：%s（类型：%s）", e.getParameterName(), e.getParameterType()));
    }

    /**
     * 方法参数类型不匹配异常
     *
     * @param e {@link MethodArgumentTypeMismatchException} 方法参数类型不匹配异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当请求参数类型与方法签名不匹配时抛出此异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型转换失败: {} -> {}", e.getValue(), e.getRequiredType());
        String expectedType = getSimpleTypeName(e.getRequiredType());
        return ReturnResult.error(REQUEST_PARAM_ERROR,
                String.format("参数'%s'类型错误，期望类型：%s，实际值：%s",
                        e.getName(), expectedType, e.getValue()));
    }

    /**
     * 类型转换异常
     *
     * @param e {@link TypeMismatchException} 类型转换异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当请求参数无法转换为目标类型时抛出此异常
     */
    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleTypeMismatchException(TypeMismatchException e) {
        log.warn("类型转换异常: {}", e.getMessage());
        return ReturnResult.error(REQUEST_PARAM_ERROR, "数据类型转换失败：" + e.getPropertyName());
    }

    /**
     * HTTP消息不可读异常 - 通常是JSON格式错误
     *
     * @param e {@link HttpMessageNotReadableException} HTTP消息不可读异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当请求体格式不正确或无法解析时抛出此异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HTTP消息不可读: {}", e.getMessage());

        Throwable cause = e.getCause();
        if (cause instanceof JsonParseException) {
            return ReturnResult.error("JSON格式错误：语法不正确，请检查JSON格式");
        } else if (cause instanceof JsonMappingException jsonMappingException) {
            return handleJsonMappingException(jsonMappingException);
        } else if (cause != null) {
            String errorMessage = convertJsonErrorMessage(cause);
            return ReturnResult.error("请求体解析失败：" + errorMessage);
        }

        return ReturnResult.error("请求体不能为空或格式不正确");
    }

    /**
     * JSON处理异常
     *
     * @param e {@link JsonProcessingException} JSON处理异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当JSON处理过程中发生错误时抛出此异常
     */
    @ExceptionHandler(JsonProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleJsonProcessingException(JsonProcessingException e) {
        log.warn("JSON处理异常: {}", e.getMessage());

        if (e instanceof JsonParseException) {
            return ReturnResult.error("JSON语法错误：" + e.getOriginalMessage());
        } else if (e instanceof InvalidFormatException invalidFormatException) {
            return ReturnResult.error(String.format("字段'%s'格式错误，期望类型：%s",
                    invalidFormatException.getPath().get(0).getFieldName(),
                    getSimpleTypeName(invalidFormatException.getTargetType())));
        } else if (e instanceof MismatchedInputException mismatchedInputException) {
            return ReturnResult.error("JSON字段类型不匹配：" +
                    mismatchedInputException.getPath().get(0).getFieldName());
        }

        return ReturnResult.error("JSON处理失败：" + e.getOriginalMessage());
    }

    /**
     * HTTP请求方法不支持异常
     *
     * @param e {@link HttpRequestMethodNotSupportedException} HTTP请求方法不支持异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当请求使用了不支持的HTTP方法时抛出此异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public <T> ReturnResult<T> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP请求方法不支持: {}", e.getMethod());
        return ReturnResult.error(REQUEST_PARAM_ERROR,
                String.format("请求方法'%s'不支持，支持的方法：%s",
                        e.getMethod(), String.join(", ", e.getSupportedMethods())));
    }

    /**
     * HTTP媒体类型不支持异常
     *
     * @param e {@link HttpMediaTypeNotSupportedException} HTTP媒体类型不支持异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当请求的Content-Type不被支持时抛出此异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public <T> ReturnResult<T> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("HTTP媒体类型不支持: {}", e.getContentType());
        return ReturnResult.error("请求的Content-Type不支持：" + e.getContentType() +
                "，支持的类型：" + e.getSupportedMediaTypes());
    }

    /**
     * 找不到处理器异常 - 404错误
     *
     * @param e {@link NoHandlerFoundException} 找不到处理器异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当请求的资源不存在时抛出此异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public <T> ReturnResult<T> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("找不到请求处理器: {} {}", e.getHttpMethod(), e.getRequestURL());
        return ReturnResult.error(RESOURCE_NOT_FOUND.getCode(),
                String.format("请求的资源不存在：%s %s", e.getHttpMethod(), e.getRequestURL()));
    }

    /**
     * 文件上传大小超限异常
     *
     * @param e {@link MaxUploadSizeExceededException} 文件上传大小超限异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当上传文件大小超过限制时抛出此异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public <T> ReturnResult<T> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传超出大小限制: {}", e.getMaxUploadSize());
        if (e.getMaxUploadSize() > 0) {
            String maxSize = StringUtils.getNetFileSizeDescription(e.getMaxUploadSize(), DECIMAL_FORMAT);
            return ReturnResult.error("文件过大，最大允许上传：" + maxSize);
        }
        return ReturnResult.error("上传文件过大，请选择较小的文件");
    }

    /**
     * SQL异常处理
     *
     * @param e {@link SQLException} SQL异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当SQL执行过程中发生错误时抛出此异常
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleSQLException(SQLException e) {
        log.error("SQL执行异常: {}", e.getMessage(), e);

        // 根据SQL状态码返回不同提示
        String sqlState = e.getSQLState();
        if (sqlState != null) {
            return switch (sqlState.substring(0, 2)) {
                case "08" -> ReturnResult.error("数据库连接异常，请稍后重试");
                case "22" -> ReturnResult.error("数据格式错误：" + extractUserFriendlyMessage(e.getMessage()));
                case "23" -> ReturnResult.error("数据完整性约束违反：" + extractConstraintMessage(e.getMessage()));
                case "42" -> ReturnResult.error("SQL语法错误，请联系管理员");
                default -> ReturnResult.error("数据库操作失败：" + extractUserFriendlyMessage(e.getMessage()));
            };
        }

        return ReturnResult.error("数据库操作异常，请联系管理员");
    }

    /**
     * SQL语法错误异常
     *
     * @param e {@link SQLSyntaxErrorException} SQL语法错误异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当SQL语句存在语法错误时抛出此异常
     */
    @ExceptionHandler(SQLSyntaxErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleSQLSyntaxErrorException(SQLSyntaxErrorException e) {
        log.error("SQL语法错误: {}", e.getMessage(), e);
        return ReturnResult.error("数据查询出现语法错误，请联系管理员");
    }

    /**
     * SQL完整性约束违反异常
     *
     * @param e {@link SQLIntegrityConstraintViolationException} SQL完整性约束违反异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当SQL操作违反完整性约束时抛出此异常
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleSQLIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException e) {
        log.warn("SQL完整性约束违反: {}", e.getMessage());
        return ReturnResult.error("数据完整性校验失败：" + extractConstraintMessage(e.getMessage()));
    }

    /**
     * 数据库访问异常
     *
     * @param e {@link DataAccessException} 数据库访问异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当数据库访问过程中发生错误时抛出此异常
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleDataAccessException(DataAccessException e) {
        log.error("数据库访问异常: {}", e.getMessage(), e);

        if (e instanceof DuplicateKeyException) {
            return ReturnResult.error("数据已存在，不能重复添加");
        } else if (e instanceof DataIntegrityViolationException) {
            return ReturnResult.error("数据完整性违反：" + extractConstraintMessage(e.getMessage()));
        }

        return ReturnResult.error("数据库访问失败，请稍后重试");
    }

    /**
     * SQL语法错误异常 - Spring包装
     *
     * @param e {@link BadSqlGrammarException} SQL语法错误异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当SQL语句存在语法错误时抛出此异常
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleBadSqlGrammarException(BadSqlGrammarException e) {
        log.error("SQL语法错误 - SQL: {}, 错误: {}", e.getSql(), e.getSQLException().getMessage());

        String sql = e.getSql().toLowerCase().trim();
        String operation = "操作";
        if (sql.startsWith("select")) {
            operation = "查询";
        } else if (sql.startsWith("insert")) {
            operation = "新增";
        } else if (sql.startsWith("update")) {
            operation = "更新";
        } else if (sql.startsWith("delete")) {
            operation = "删除";
        }

        return ReturnResult.error(String.format("数据库%s操作失败，请联系管理员", operation));
    }

    /**
     * 网络相关异常
     *
     * @param e {@link Exception} 网络相关异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当网络连接失败或超时时抛出此异常
     */
    @ExceptionHandler({UnknownHostException.class, ConnectException.class, SocketTimeoutException.class, TimeoutException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public <T> ReturnResult<T> handleNetworkException(Exception e) {
        log.error("网络异常: {}", e.getMessage(), e);

        if (e instanceof UnknownHostException) {
            return ReturnResult.error("网络连接失败：无法解析主机地址");
        } else if (e instanceof ConnectException) {
            return ReturnResult.error("网络连接失败：连接被拒绝");
        } else if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
            return ReturnResult.error("网络请求超时，请稍后重试");
        }

        return ReturnResult.error("网络异常，请检查网络连接");
    }

    /**
     * 远程执行异常
     *
     * @param e {@link RemoteExecutionException} 远程执行异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当远程服务调用失败时抛出此异常
     */
    @ExceptionHandler(RemoteExecutionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleRemoteExecutionException(RemoteExecutionException e) {
        log.error("远程服务调用异常: {}", e.getMessage(), e);

        String message = e.getMessage();
        if (message != null && message.contains("Auth fail")) {
            return ReturnResult.error("远程服务认证失败，请检查登录状态");
        } else if (message != null && message.contains("timeout")) {
            return ReturnResult.error("远程服务调用超时，请稍后重试");
        }

        return ReturnResult.error("远程服务调用失败：" + extractUserFriendlyMessage(message));
    }

    /**
     * 认证异常
     *
     * @param e {@link AuthenticationException} 认证异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当用户身份认证失败时抛出此异常
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public <T> ReturnResult<T> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证异常: {}", e.getMessage());
        return ReturnResult.error(RESULT_ACCESS_UNAUTHORIZED.getCode(), "身份认证失败，请重新登录");
    }

    /**
     * 业务异常
     *
     * @param e {@link BusinessException} 业务异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当业务逻辑处理失败时抛出此异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        String message = e.getLocalizedMessage();
        if (StringUtils.isBlank(message)) {
            message = "业务处理失败";
        }
        return ReturnResult.error(message);
    }

    /**
     * 运行时消息异常
     *
     * @param e {@link RuntimeMessageException} 运行时消息异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当运行时发生消息相关错误时抛出此异常
     */
    @ExceptionHandler(RuntimeMessageException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleRuntimeMessageException(RuntimeMessageException e) {
        log.warn("运行时消息异常: {}", e.getMessage());
        return ReturnResult.error(e.getMessage());
    }

    /**
     * 非法参数异常
     *
     * @param e {@link IllegalArgumentException} 非法参数异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当传递了非法参数时抛出此异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常: {}", e.getMessage());

        String message = e.getMessage();
        if (message != null) {
            if (message.contains("Unable to parse url")) {
                return ReturnResult.error("URL地址格式不正确，请检查URL格式");
            } else if (message.contains("Invalid UUID")) {
                return ReturnResult.error("UUID格式不正确");
            }
        }

        return ReturnResult.error("参数不合法：" + extractUserFriendlyMessage(message));
    }

    /**
     * 类型转换异常
     *
     * @param e {@link ClassCastException} 类型转换异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当对象类型转换失败时抛出此异常
     */
    @ExceptionHandler(ClassCastException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleClassCastException(ClassCastException e) {
        log.error("类型转换异常: {}", e.getMessage(), e);
        return ReturnResult.error("数据类型转换失败，请检查数据格式");
    }

    /**
     * 空指针异常
     *
     * @param e {@link NullPointerException} 空指针异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当尝试访问空对象的属性或方法时抛出此异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常: {}", e.getMessage(), e);
        return ReturnResult.error("系统处理异常，缺少必要的数据");
    }

    /**
     * 数组越界异常
     *
     * @param e {@link ArrayIndexOutOfBoundsException} 数组越界异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当访问数组时索引超出范围时抛出此异常
     */
    @ExceptionHandler(ArrayIndexOutOfBoundsException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleArrayIndexOutOfBoundsException(ArrayIndexOutOfBoundsException e) {
        log.error("数组越界异常: {}", e.getMessage(), e);
        return ReturnResult.error("数据访问越界，请检查数据完整性");
    }

    /**
     * 数字格式异常
     *
     * @param e {@link NumberFormatException} 数字格式异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当字符串无法转换为数字时抛出此异常
     */
    @ExceptionHandler(NumberFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleNumberFormatException(NumberFormatException e) {
        log.warn("数字格式异常: {}", e.getMessage());
        return ReturnResult.error("数字格式不正确，请输入有效的数字");
    }

    /**
     * 运行时异常 - 兜底处理
     *
     * @param e        {@link RuntimeException} 运行时异常
     * @param response {@link HttpServletResponse} HTTP响应对象
     * @return {@link ReturnResult} 错误响应结果
     * @example 当发生未被捕获的运行时异常时抛出此异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleRuntimeException(RuntimeException e, HttpServletResponse response) {
        log.error("运行时异常: {}", e.getMessage(), e);
        response.setContentType("application/json");

        // 特殊的运行时异常处理
        if ("org.apache.ibatis.exceptions.TooManyResultsException".equals(e.getClass().getName())) {
            return ReturnResult.error("数据查询异常：期望返回一条记录，但查询到多条数据");
        }

        Throwable cause = e.getCause();
        if (cause instanceof UnsupportedOperationException) {
            return ReturnResult.error("当前操作不被支持，请联系管理员");
        } else if (cause instanceof RemoteExecutionException) {
            return handleRemoteExecutionException((RemoteExecutionException) cause);
        } else if (cause instanceof IllegalArgumentException) {
            return handleIllegalArgumentException((IllegalArgumentException) cause);
        }

        String message = e.getMessage();
        if (message != null && message.contains("Data truncation: Data too long for column")) {
            Matcher matcher = DATA_TOO_LONG_PATTERN.matcher(message);
            if (matcher.find()) {
                return ReturnResult.error(String.format("字段'%s'数据长度超出限制",
                        NamingCase.toCamelCase(matcher.group(1))));
            }
            return ReturnResult.error("数据长度超出限制，请减少输入内容");
        }

        return ReturnResult.error("系统运行异常，请稍后重试或联系管理员");
    }

    /**
     * 通用异常 - 最后的兜底
     *
     * @param e {@link Exception} 通用异常
     * @return {@link ReturnResult} 错误响应结果
     * @example 当发生未被捕获的通用异常时抛出此异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ReturnResult.error("系统异常，请联系管理员");
    }

    // ==================== 私有工具方法 ====================

    /**
     * 处理JSON映射异常
     *
     * @param e {@link JsonMappingException} JSON映射异常
     * @return {@link ReturnResult} 错误响应结果
     */
    private <T> ReturnResult<T> handleJsonMappingException(JsonMappingException e) {
        if (e instanceof InvalidFormatException invalidFormatException) {
            String fieldName = invalidFormatException.getPath().isEmpty() ?
                    "未知字段" : invalidFormatException.getPath().get(0).getFieldName();
            String expectedType = getSimpleTypeName(invalidFormatException.getTargetType());
            return ReturnResult.error(String.format("字段'%s'格式错误，期望类型：%s，实际值：%s",
                    fieldName, expectedType, invalidFormatException.getValue()));
        } else if (e instanceof MismatchedInputException mismatchedInputException) {
            String fieldName = mismatchedInputException.getPath().isEmpty() ?
                    "根对象" : mismatchedInputException.getPath().get(0).getFieldName();
            return ReturnResult.error(String.format("字段'%s'类型不匹配或格式错误", fieldName));
        }
        return ReturnResult.error("JSON字段映射错误：" + e.getOriginalMessage());
    }

    /**
     * 转换JSON错误消息
     *
     * @param throwable {@link Throwable} 异常对象
     * @return 转换后的错误消息
     */
    private String convertJsonErrorMessage(Throwable throwable) {
        String error = throwable.toString();
        Matcher matcher = CONVERTER_PATTERN.matcher(error);
        if (matcher.find()) {
            String field = matcher.group(1);
            return String.format("字段'%s'格式错误", field);
        }
        return "格式不正确";
    }

    /**
     * 获取简化的类型名称
     *
     * @param type {@link Class} 类型
     * @return 简化的类型名称
     */
    private String getSimpleTypeName(Class<?> type) {
        if (type == null) return "未知类型";

        return switch (type.getSimpleName()) {
            case "Integer" -> "整数";
            case "Long" -> "长整数";
            case "Double" -> "小数";
            case "Boolean" -> "布尔值(true/false)";
            case "String" -> "字符串";
            case "Date" -> "日期";
            case "LocalDateTime" -> "日期时间";
            case "LocalDate" -> "日期";
            case "LocalTime" -> "时间";
            case "BigDecimal" -> "精确小数";
            default -> type.getSimpleName();
        };
    }

    /**
     * 提取用户友好的错误消息
     *
     * @param message 错误消息
     * @return 用户友好的错误消息
     */
    private String extractUserFriendlyMessage(String message) {
        if (StringUtils.isBlank(message)) return "操作失败";

        // 如果消息包含中文，直接返回
        if (Validator.hasChinese(message)) {
            return message;
        }

        // 常见英文错误消息翻译
        if (message.contains("Connection refused")) {
            return "连接被拒绝";
        } else if (message.contains("timeout")) {
            return "操作超时";
        } else if (message.contains("Access denied")) {
            return "访问被拒绝";
        } else if (message.contains("Invalid")) {
            return "数据无效";
        }

        return "操作异常";
    }

    /**
     * 提取约束错误消息
     *
     * @param message 错误消息
     * @return 约束错误消息
     */
    private String extractConstraintMessage(String message) {
        if (StringUtils.isBlank(message)) return "数据约束验证失败";

        Matcher duplicateMatcher = DUPLICATE_ENTRY_PATTERN.matcher(message);
        if (duplicateMatcher.find()) {
            return String.format("数据'%s'已存在，不能重复", duplicateMatcher.group(1));
        }

        if (message.contains("foreign key constraint")) {
            return "关联数据不存在，请先确保相关数据完整";
        } else if (message.contains("unique")) {
            return "数据重复，请检查唯一性约束";
        } else if (message.contains("not null")) {
            return "必需字段不能为空";
        }

        return extractUserFriendlyMessage(message);
    }
}
