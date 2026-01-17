package com.chua.starter.common.support.api.response;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.exception.AuthenticationException;
import com.chua.common.support.core.exception.RemoteExecutionException;
import com.chua.common.support.math.unit.name.NamingCase;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.common.support.base.validator.Validator;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.common.support.exception.RuntimeMessageException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.chua.common.support.lang.code.ReturnCode.*;

import lombok.extern.slf4j.Slf4j;

/**
 * 统一异常处理器
 * <p>
 * 提供详细的中文错误提示，帮助快速定位问题。
 * </p>
 *
 * @author CH
 * @since 2023-08-01
 * @version 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionAdvice {
        /** 是否为生产环境（生产环境隐藏技术细节） */
    private static boolean isProduction = false;
    
    /** 是否在响应中返回详细错误信息（字段级别错误） */
    private static boolean returnDetailedErrors = true;
    
    /** 请求追踪ID的Header名称 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    
    /**
     * 设置是否为生产环境
     * @param production 是否为生产环境
     */
    public static void setProduction(boolean production) {
        isProduction = production;
    }
    
    /**
     * 设置是否返回详细错误信息
     * @param detailed 是否返回详细错误
     */
    public static void setReturnDetailedErrors(boolean detailed) {
        returnDetailedErrors = detailed;
    }

    private static final Pattern DATA_TOO_LONG_PATTERN = Pattern.compile("Data too long for column '([^']*)' at row");
    private static final Pattern DUPLICATE_ENTRY_PATTERN = Pattern.compile("Duplicate entry '([^']*)' for key '([^']*)'");
    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile("FOREIGN KEY \\(`([^`]*)`\\)");
    private static final Pattern COLUMN_PATTERN = Pattern.compile("Column '([^']*)'");
    private static final Pattern CONVERTER_PATTERN = Pattern.compile("\\[\"(.*?)\"]+");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##");

    // ==================== 参数校验异常 ====================

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleBindException(BindException e) {
        logWarn("表单参数验证失败", e);
        
        List<FieldError> fieldErrors = e.getFieldErrors();
        if (returnDetailedErrors && !fieldErrors.isEmpty()) {
            Map<String, String> errors = buildFieldErrorMap(fieldErrors);
            String firstError = fieldErrors.get(0).getDefaultMessage();
            return buildValidationError("表单参数验证失败", firstError, errors);
        }
        
        String msg = e.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ReturnResult.error(REQUEST_PARAM_ERROR, "表单参数验证失败：" + msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        logWarn("JSON参数验证失败", e);
        
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        if (returnDetailedErrors && !fieldErrors.isEmpty()) {
            Map<String, String> errors = buildFieldErrorMap(fieldErrors);
            String firstError = fieldErrors.get(0).getDefaultMessage();
            return buildValidationError("请求参数验证失败", firstError, errors);
        }
        
        String msg = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ReturnResult.error(REQUEST_PARAM_ERROR, "请求参数验证失败：" + msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleConstraintViolationException(ConstraintViolationException e) {
        logWarn("数据验证约束违反", e);
        
        if (returnDetailedErrors) {
            Map<String, String> errors = new LinkedHashMap<>();
            for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
                String path = violation.getPropertyPath().toString();
                // 提取最后一个字段名
                int lastDot = path.lastIndexOf('.');
                String fieldName = lastDot >= 0 ? path.substring(lastDot + 1) : path;
                errors.put(fieldName, violation.getMessage());
            }
            String firstError = e.getConstraintViolations().iterator().next().getMessage();
            return buildValidationError("数据验证失败", firstError, errors);
        }
        
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return ReturnResult.error(REQUEST_PARAM_ERROR, "数据验证失败：" + msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logWarn("缺少必需参数: " + e.getParameterName(), e);
        String typeName = getSimpleTypeName(e.getParameterType());
        return ReturnResult.error(REQUEST_PARAM_EMPTY,
                String.format("缺少必需参数'%s'，类型：%s", e.getParameterName(), typeName));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logWarn("参数类型转换失败", e);
        String expectedType = getSimpleTypeName(e.getRequiredType());
        String actualValue = e.getValue() != null ? String.valueOf(e.getValue()) : "空值";
        // 截断过长的值
        if (actualValue.length() > 50) {
            actualValue = actualValue.substring(0, 47) + "...";
        }
        return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR,
                String.format("参数'%s'格式错误，期望%s，收到：%s",
                        e.getName(), expectedType, actualValue));
    }

    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleTypeMismatchException(TypeMismatchException e) {
        logWarn("类型转换异常", e);
        String propertyName = e.getPropertyName();
        if (StringUtils.isBlank(propertyName)) {
            return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "数据类型转换失败");
        }
        return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "参数'" + propertyName + "'类型转换失败");
    }

    // ==================== HTTP/JSON 解析异常 ====================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        logWarn("HTTP消息不可读", e);
        Throwable cause = e.getCause();
        
        if (cause instanceof JsonParseException jpe) {
            String location = "";
            if (jpe.getLocation() != null) {
                location = String.format("（第%d行，第%d列）", 
                        jpe.getLocation().getLineNr(), jpe.getLocation().getColumnNr());
            }
            return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "JSON格式错误" + location + "：语法不正确");
        } else if (cause instanceof JsonMappingException jsonMappingException) {
            return handleJsonMappingException(jsonMappingException);
        } else if (cause != null) {
            String errorMessage = convertJsonErrorMessage(cause);
            return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "请求体解析失败：" + errorMessage);
        }
        return ReturnResult.error(REQUEST_PARAM_EMPTY, "请求体不能为空");
    }

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

    // ==================== HTTP 请求异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public <T> ReturnResult<T> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        logWarn("HTTP请求方法不支持: " + e.getMethod(), e);
        String[] supported = e.getSupportedMethods();
        String supportedStr = supported != null ? String.join("/", supported) : "未知";
        return ReturnResult.error(REQUEST_PARAM_ERROR,
                String.format("不支持%s请求，请使用%s方法", e.getMethod(), supportedStr));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public <T> ReturnResult<T> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        logWarn("HTTP媒体类型不支持: " + e.getContentType(), e);
        return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, 
                "不支持的请求格式，请使用JSON格式提交数据");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public <T> ReturnResult<T> handleNoHandlerFoundException(NoHandlerFoundException e) {
        logWarn("找不到请求处理器", e);
        return ReturnResult.error(RESOURCE_NOT_FOUND, "请求的接口不存在，请检查URL是否正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public <T> ReturnResult<T> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logWarn("文件上传超出大小限制", e);
        if (e.getMaxUploadSize() > 0) {
            String maxSize = StringUtils.getNetFileSizeDescription(e.getMaxUploadSize(), DECIMAL_FORMAT);
            return ReturnResult.error(FILE_SIZE_EXCEEDED, "文件大小超过限制，最大允许" + maxSize);
        }
        return ReturnResult.error(FILE_SIZE_EXCEEDED, "文件大小超过限制，请压缩后重试");
    }

    // ==================== 数据库异常 ====================

    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleSQLException(SQLException e) {
        logError("SQL执行异常", e);
        
        // 生产环境隐藏SQL技术细节
        if (isProduction) {
            return ReturnResult.error(DATABASE_ERROR, "数据操作失败，请稍后重试");
        }
        
        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.length() >= 2) {
            return switch (sqlState.substring(0, 2)) {
                case "08" -> ReturnResult.error(DATABASE_ERROR, "数据库连接异常，请稍后重试");
                case "22" -> ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, 
                        "数据格式错误：" + extractUserFriendlyMessage(e.getMessage()));
                case "23" -> ReturnResult.error(DATA_CONFLICT, extractConstraintMessage(e.getMessage()));
                case "42" -> ReturnResult.error(DATABASE_ERROR, "数据库查询异常");
                default -> ReturnResult.error(DATABASE_ERROR, "数据库操作失败");
            };
        }
        return ReturnResult.error(DATABASE_ERROR, "数据操作失败，请稍后重试");
    }

    @ExceptionHandler(SQLSyntaxErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleSQLSyntaxErrorException(SQLSyntaxErrorException e) {
        logError("SQL语法错误", e);
        return ReturnResult.error(DATABASE_ERROR, "数据查询失败，请稍后重试");
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleSQLIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException e) {
        logWarn("SQL完整性约束违反", e);
        return ReturnResult.error(DATA_CONFLICT, extractConstraintMessage(e.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleDataAccessException(DataAccessException e) {
        logError("数据库访问异常", e);
        if (e instanceof DuplicateKeyException dke) {
            String msg = extractConstraintMessage(dke.getMessage());
            return ReturnResult.error(DATA_ALREADY_EXISTS, msg != null ? msg : "数据已存在，请勿重复提交");
        } else if (e instanceof DataIntegrityViolationException dive) {
            return ReturnResult.error(DATA_CONFLICT, extractConstraintMessage(dive.getMessage()));
        }
        return ReturnResult.error(DATABASE_ERROR, "数据操作失败，请稍后重试");
    }

    @ExceptionHandler(BadSqlGrammarException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleBadSqlGrammarException(BadSqlGrammarException e) {
        logError("SQL语法错误 - SQL: " + e.getSql(), e);
        return ReturnResult.error(DATABASE_ERROR, "数据操作失败，请稍后重试");
    }

    // ==================== 网络/远程调用异常 ====================

    @ExceptionHandler({UnknownHostException.class, ConnectException.class, SocketTimeoutException.class, TimeoutException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public <T> ReturnResult<T> handleNetworkException(Exception e) {
        logError("网络异常", e);
        if (e instanceof UnknownHostException) {
            return ReturnResult.error(THIRD_PARTY_ERROR, "网络连接失败，请检查网络设置");
        } else if (e instanceof ConnectException) {
            return ReturnResult.error(THIRD_PARTY_ERROR, "服务连接失败，请稍后重试");
        } else if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
            return ReturnResult.error(SYSTEM_EXECUTION_TIMEOUT, "请求超时，请稍后重试");
        }
        return ReturnResult.error(THIRD_PARTY_ERROR, "网络异常，请稍后重试");
    }

    // ==================== 异步/并发异常 ====================

    @ExceptionHandler(CompletionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleCompletionException(CompletionException e) {
        Throwable cause = e.getCause();
        logError("异步任务执行异常", cause != null ? cause : e);
        
        if (cause != null) {
            return handleAsyncCause(cause);
        }
        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "操作处理失败，请稍后重试");
    }

    @ExceptionHandler(ExecutionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        logError("任务执行异常", cause != null ? cause : e);
        
        if (cause != null) {
            return handleAsyncCause(cause);
        }
        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "操作处理失败，请稍后重试");
    }

    @ExceptionHandler(CancellationException.class)
    @ResponseStatus(HttpStatus.GONE)
    public <T> ReturnResult<T> handleCancellationException(CancellationException e) {
        logWarn("任务已取消", e);
        return ReturnResult.error(OPERATION_NOT_ALLOWED, "操作已取消");
    }

    @ExceptionHandler(RejectedExecutionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public <T> ReturnResult<T> handleRejectedExecutionException(RejectedExecutionException e) {
        logError("任务被拒绝执行", e);
        return ReturnResult.error(SERVICE_UNAVAILABLE, "系统繁忙，请稍后重试");
    }

    @ExceptionHandler(InterruptedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleInterruptedException(InterruptedException e) {
        logWarn("任务被中断", e);
        Thread.currentThread().interrupt();
        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "操作被中断，请重试");
    }

    @ExceptionHandler(RemoteExecutionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleRemoteExecutionException(RemoteExecutionException e) {
        logError("远程服务调用异常", e);
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("Auth fail") || message.contains("401") || message.contains("Unauthorized")) {
                return ReturnResult.error(USER_NOT_LOGIN, "远程服务认证失败，请重新登录");
            } else if (message.contains("timeout") || message.contains("timed out")) {
                return ReturnResult.error(SYSTEM_EXECUTION_TIMEOUT, "远程服务响应超时，请稍后重试");
            } else if (message.contains("Connection refused") || message.contains("503")) {
                return ReturnResult.error(SERVICE_UNAVAILABLE, "远程服务暂时不可用，请稍后重试");
            }
        }
        return ReturnResult.error(THIRD_PARTY_ERROR, "远程服务调用失败，请稍后重试");
    }

    // ==================== 认证/授权异常 ====================

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public <T> ReturnResult<T> handleAuthenticationException(AuthenticationException e) {
        logWarn("认证异常", e);
        return ReturnResult.error(USER_NOT_LOGIN, "登录已过期，请重新登录");
    }

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleBusinessException(BusinessException e) {
        logWarn("业务异常", e);
        String message = e.getLocalizedMessage();
        if (StringUtils.isBlank(message)) {
            message = "操作失败，请稍后重试";
        }
        // 业务异常直接返回原始消息，因为通常是开发者有意设置的
        return ReturnResult.error(BUSINESS_ERROR, message);
    }

    @ExceptionHandler(RuntimeMessageException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleRuntimeMessageException(RuntimeMessageException e) {
        logWarn("运行时消息异常", e);
        String message = e.getMessage();
        return ReturnResult.error(BUSINESS_ERROR, StringUtils.isBlank(message) ? "操作失败" : message);
    }

    // ==================== 通用异常 ====================

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleIllegalArgumentException(IllegalArgumentException e) {
        logWarn("非法参数异常", e);
        String message = e.getMessage();
        if (message != null) {
            // 常见参数错误的友好提示
            if (message.contains("Unable to parse url") || message.contains("Malformed URL")) {
                return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "URL地址格式不正确");
            } else if (message.contains("Invalid UUID") || message.contains("Invalid uuid")) {
                return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "ID格式不正确");
            } else if (message.contains("Invalid date") || message.contains("DateTimeParseException")) {
                return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "日期格式不正确");
            } else if (message.contains("must not be null") || message.contains("cannot be null")) {
                return ReturnResult.error(REQUEST_PARAM_EMPTY, "必填参数不能为空");
            } else if (message.contains("must not be blank") || message.contains("cannot be blank")) {
                return ReturnResult.error(REQUEST_PARAM_EMPTY, "必填参数不能为空");
            } else if (Validator.hasChinese(message)) {
                // 如果消息包含中文，说明是开发者有意设置的，直接返回
                return ReturnResult.error(REQUEST_PARAM_ERROR, message);
            }
        }
        return ReturnResult.error(REQUEST_PARAM_ERROR, "参数格式不正确");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public <T> ReturnResult<T> handleIllegalStateException(IllegalStateException e) {
        logWarn("非法状态异常", e);
        String message = e.getMessage();
        if (message != null && Validator.hasChinese(message)) {
            return ReturnResult.error(BUSINESS_ERROR, message);
        }
        return ReturnResult.error(BUSINESS_ERROR, "操作状态异常，请刷新后重试");
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public <T> ReturnResult<T> handleUnsupportedOperationException(UnsupportedOperationException e) {
        logWarn("不支持的操作", e);
        return ReturnResult.error(OPERATION_NOT_ALLOWED, "当前操作暂不支持");
    }

    @ExceptionHandler(ClassCastException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleClassCastException(ClassCastException e) {
        logError("类型转换异常", e);
        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "数据处理异常，请稍后重试");
    }

    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleNullPointerException(NullPointerException e) {
        logError("空指针异常", e);
        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "系统处理异常，请稍后重试");
    }

    @ExceptionHandler(ArrayIndexOutOfBoundsException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleArrayIndexOutOfBoundsException(ArrayIndexOutOfBoundsException e) {
        logError("数组越界异常", e);
        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "数据处理异常，请稍后重试");
    }

    @ExceptionHandler(NumberFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> handleNumberFormatException(NumberFormatException e) {
        logWarn("数字格式异常", e);
        return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "数字格式不正确");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleRuntimeException(RuntimeException e, HttpServletResponse response) {
        logError("运行时异常", e);
        response.setContentType("application/json;charset=UTF-8");

        // MyBatis 多结果异常
        if ("org.apache.ibatis.exceptions.TooManyResultsException".equals(e.getClass().getName())) {
            return ReturnResult.error(DATA_CONFLICT, "查询结果不唯一，请检查数据");
        }

        // 处理嵌套的原因异常
        Throwable cause = e.getCause();
        if (cause instanceof UnsupportedOperationException) {
            return handleUnsupportedOperationException((UnsupportedOperationException) cause);
        } else if (cause instanceof RemoteExecutionException) {
            return handleRemoteExecutionException((RemoteExecutionException) cause);
        } else if (cause instanceof IllegalArgumentException) {
            return handleIllegalArgumentException((IllegalArgumentException) cause);
        } else if (cause instanceof IllegalStateException) {
            return handleIllegalStateException((IllegalStateException) cause);
        }

        // 数据长度超限
        String message = e.getMessage();
        if (message != null && message.contains("Data truncation: Data too long for column")) {
            Matcher matcher = DATA_TOO_LONG_PATTERN.matcher(message);
            if (matcher.find()) {
                String fieldName = NamingCase.toCamelCase(matcher.group(1));
                return ReturnResult.error(REQUEST_PARAM_OUT_OF_RANGE, 
                        String.format("'%s'内容过长，请缩减后重试", fieldName));
            }
            return ReturnResult.error(REQUEST_PARAM_OUT_OF_RANGE, "输入内容过长，请缩减后重试");
        }

        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "系统繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleException(Exception e) {
        logError("系统异常", e);
        return ReturnResult.error(SYSTEM_SERVER_OTHER_ERROR, "系统繁忙，请稍后重试");
    }

    // ==================== 私有工具方法 ====================

    /**
     * 构建字段错误映射
     */
    private Map<String, String> buildFieldErrorMap(List<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : fieldErrors) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }

    /**
     * 构建参数校验错误结果（带字段详情）
     */
    @SuppressWarnings("unchecked")
    private <T> ReturnResult<T> buildValidationError(String title, String firstError, Map<String, String> errors) {
        ReturnResult<Map<String, String>> result = ReturnResult.error(REQUEST_PARAM_ERROR, title + "：" + firstError);
        result.setData(errors);
        return (ReturnResult<T>) result;
    }

    private <T> ReturnResult<T> handleJsonMappingException(JsonMappingException e) {
        if (e instanceof InvalidFormatException ife) {
            String fieldName = ife.getPath().isEmpty() ? "未知字段" : ife.getPath().get(0).getFieldName();
            String expectedType = getSimpleTypeName(ife.getTargetType());
            Object value = ife.getValue();
            String valueStr = value != null ? String.valueOf(value) : "空";
            if (valueStr.length() > 30) {
                valueStr = valueStr.substring(0, 27) + "...";
            }
            return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, 
                    String.format("'%s'格式错误，需要%s，收到：%s", fieldName, expectedType, valueStr));
        } else if (e instanceof MismatchedInputException mie) {
            String fieldName = mie.getPath().isEmpty() ? "请求数据" : mie.getPath().get(0).getFieldName();
            return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, String.format("'%s'格式不正确", fieldName));
        }
        return ReturnResult.error(REQUEST_PARAM_FORMAT_ERROR, "请求数据格式错误");
    }

    private String convertJsonErrorMessage(Throwable throwable) {
        String error = throwable.toString();
        Matcher matcher = CONVERTER_PATTERN.matcher(error);
        if (matcher.find()) {
            String field = matcher.group(1);
            return String.format("'%s'格式错误", field);
        }
        return "格式不正确";
    }

    /**
     * 获取类型的友好名称
     */
    private String getSimpleTypeName(Class<?> type) {
        if (type == null) return "有效值";
        return switch (type.getSimpleName()) {
            case "Integer", "int" -> "整数";
            case "Long", "long" -> "整数";
            case "Double", "double", "Float", "float" -> "数字";
            case "Boolean", "boolean" -> "布尔值(true/false)";
            case "String" -> "文本";
            case "Date", "LocalDate" -> "日期(yyyy-MM-dd)";
            case "LocalDateTime" -> "日期时间(yyyy-MM-dd HH:mm:ss)";
            case "LocalTime" -> "时间(HH:mm:ss)";
            case "BigDecimal" -> "数字";
            case "List", "ArrayList" -> "列表";
            case "Map", "HashMap" -> "对象";
            default -> type.getSimpleName();
        };
    }

    /**
     * 获取参数类型的友好名称
     */
    private String getSimpleTypeName(String typeName) {
        if (typeName == null) return "有效值";
        return switch (typeName) {
            case "int", "Integer" -> "整数";
            case "long", "Long" -> "整数";
            case "double", "Double", "float", "Float" -> "数字";
            case "boolean", "Boolean" -> "布尔值";
            case "String" -> "文本";
            default -> typeName;
        };
    }

    /**
     * 提取用户友好的错误信息
     */
    private String extractUserFriendlyMessage(String message) {
        if (StringUtils.isBlank(message)) return "操作失败";
        // 如果包含中文，认为是开发者设置的友好消息
        if (Validator.hasChinese(message)) {
            return message;
        }
        // 常见英文错误的友好转换
        if (message.contains("Connection refused")) {
            return "服务连接失败";
        } else if (message.contains("timeout") || message.contains("timed out")) {
            return "操作超时";
        } else if (message.contains("Access denied") || message.contains("Permission denied")) {
            return "访问被拒绝";
        } else if (message.contains("Invalid") || message.contains("Illegal")) {
            return "数据无效";
        } else if (message.contains("not found") || message.contains("Not Found")) {
            return "数据不存在";
        } else if (message.contains("already exists") || message.contains("Duplicate")) {
            return "数据已存在";
        }
        return "操作失败";
    }

    /**
     * 提取数据库约束错误的友好信息
     */
    private String extractConstraintMessage(String message) {
        if (StringUtils.isBlank(message)) return "数据校验失败";
        
        // 唯一约束冲突 - 提取重复的值
        Matcher duplicateMatcher = DUPLICATE_ENTRY_PATTERN.matcher(message);
        if (duplicateMatcher.find()) {
            String value = duplicateMatcher.group(1);
            // 隐藏过长的值
            if (value.length() > 20) {
                value = value.substring(0, 17) + "...";
            }
            return String.format("'%s'已存在，请勿重复提交", value);
        }
        
        // 外键约束 - 尝试提取字段名
        if (message.contains("foreign key constraint") || message.contains("FOREIGN KEY")) {
            Matcher fkMatcher = FOREIGN_KEY_PATTERN.matcher(message);
            if (fkMatcher.find()) {
                String field = NamingCase.toCamelCase(fkMatcher.group(1));
                return String.format("'%s'关联的数据不存在", field);
            }
            return "关联数据不存在，请先添加相关数据";
        }
        
        // 非空约束
        if (message.contains("cannot be null") || message.contains("doesn't have a default value")) {
            Matcher colMatcher = COLUMN_PATTERN.matcher(message);
            if (colMatcher.find()) {
                String field = NamingCase.toCamelCase(colMatcher.group(1));
                return String.format("'%s'不能为空", field);
            }
            return "必填字段不能为空";
        }
        
        // 唯一约束
        if (message.contains("unique") || message.contains("UNIQUE")) {
            return "数据已存在，请勿重复提交";
        }
        
        return "数据校验失败，请检查输入";
    }

    /**
     * 处理异步任务内部异常
     */
    @SuppressWarnings("unchecked")
    private <T> ReturnResult<T> handleAsyncCause(Throwable cause) {
        if (cause instanceof IllegalArgumentException iae) {
            return (ReturnResult<T>) handleIllegalArgumentException(iae);
        }
        if (cause instanceof IllegalStateException ise) {
            return (ReturnResult<T>) handleIllegalStateException(ise);
        }
        if (cause instanceof BusinessException be) {
            return (ReturnResult<T>) handleBusinessException(be);
        }
        if (cause instanceof RuntimeMessageException rme) {
            return (ReturnResult<T>) handleRuntimeMessageException(rme);
        }
        if (cause instanceof AuthenticationException ae) {
            return (ReturnResult<T>) handleAuthenticationException(ae);
        }
        if (cause instanceof TimeoutException) {
            return ReturnResult.error(SYSTEM_EXECUTION_TIMEOUT, "操作超时，请稍后重试");
        }
        if (cause instanceof DataAccessException dae) {
            return (ReturnResult<T>) handleDataAccessException(dae);
        }
        logError("异步任务未知异常", cause);
        return ReturnResult.error(SYSTEM_EXECUTION_ERROR, "操作处理失败，请稍后重试");
    }

    // ==================== 日志工具方法 ====================

    /**
     * 记录警告日志（带请求信息）
     */
    private void logWarn(String message, Throwable e) {
        String uri = getRequestUri();
        String traceId = getOrCreateTraceId();
        log.warn("[{}] {} - URI: {}, 错误: {}", traceId, message, uri, e.getMessage());
    }

    /**
     * 记录错误日志（带请求信息和堆栈）
     */
    private void logError(String message, Throwable e) {
        String uri = getRequestUri();
        String traceId = getOrCreateTraceId();
        log.error("[{}] {} - URI: {}, 错误: {}", traceId, message, uri, e.getMessage(), e);
    }

    /**
     * 获取当前请求URI
     */
    private String getRequestUri() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getMethod() + " " + request.getRequestURI();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "unknown";
    }

    /**
     * 获取或创建请求追踪ID
     */
    private String getOrCreateTraceId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String traceId = request.getHeader(TRACE_ID_HEADER);
                if (StringUtils.isNotBlank(traceId)) {
                    return traceId;
                }
                // 从请求属性中获取或生成
                traceId = (String) request.getAttribute(TRACE_ID_HEADER);
                if (StringUtils.isBlank(traceId)) {
                    traceId = UUID.randomUUID().toString().substring(0, 8);
                    request.setAttribute(TRACE_ID_HEADER, traceId);
                }
                return traceId;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "no-trace";
    }
}

