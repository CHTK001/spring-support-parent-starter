// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.api.response;

import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.lang.NamingCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.chua.common.support.lang.code.ReturnCode.DATA_ALREADY_EXISTS;
import static com.chua.common.support.lang.code.ReturnCode.DATA_CONFLICT;
import static com.chua.common.support.lang.code.ReturnCode.DATABASE_ERROR;

/**
 * 数据访问异常处理器（Spring DAO 体系）。
 *
 * <p>从 {@code ApiExceptionAdvice} 拆出：本类是唯一引用
 * {@code org.springframework.dao.*} 的组件，并以
 * {@code @ConditionalOnClass(DataAccessException.class)} 门控——
 * classpath 无 Spring JDBC 的宿主不会装配本类，也不会在启动时
 * 解析缺失的异常类型；有 JDBC 的宿主行为与拆分前完全一致。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestControllerAdvice
@ConditionalOnClass(DataAccessException.class)
public class DataAccessExceptionAdvice {

    /**
     * 日志。
     */
    private static final Logger log = LoggerFactory.getLogger(DataAccessExceptionAdvice.class);

    /**
     * 请求追踪标识的头部名称（与 ApiExceptionAdvice 保持一致）。
     */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 重复_entry_模式。
     */
    private static final Pattern DUPLICATE_ENTRY_PATTERN = Pattern.compile("Duplicate entry '([^']*)' for key '([^']*)'");
    /**
     * 国外_键_模式。
     */
    private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile("FOREIGN KEY \\(`([^`]*)`\\)");
    /**
     * Column_模式。
     */
    private static final Pattern COLUMN_PATTERN = Pattern.compile("Column '([^']*)'");

    /**
     * 处理数据access异常。
     *
     * @param e e
     * @return 处理数据access异常的结果
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> ReturnResult<T> handleDataAccessException(DataAccessException e) {
        logError("数据库访问异常", e);
        if (e instanceof DuplicateKeyException dke) {
            String msg = extractConstraintMessage(dke.getMessage());
            return ReturnResult.error(DATA_ALREADY_EXISTS.getCode(), msg != null ? msg : "数据已存在，请勿重复提交");
        } else if (e instanceof DataIntegrityViolationException dive) {
            return ReturnResult.error(DATA_CONFLICT.getCode(), extractConstraintMessage(dive.getMessage()));
        }
        return ReturnResult.error(DATABASE_ERROR.getCode(), "数据操作失败，请稍后重试");
    }

    /**
     * 提取数据库约束错误的友好信息。
     *
     * @param message 消息
     * @return extractconstraint消息的结果
     */
    private String extractConstraintMessage(String message) {
        if (StringUtils.isBlank(message)) {
            return "数据校验失败";
        }

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
     * 记录错误日志（带请求信息和堆栈）。
     *
     * @param message 消息
     * @param e       e
     */
    private void logError(String message, Throwable e) {
        String uri = getRequestUri();
        String traceId = getOrCreateTraceId();
        log.error("[{}] {} - URI: {}, 错误: {}", traceId, message, uri, e.getMessage(), e);
    }

    /**
     * 获取当前请求URI。
     *
     * @return 获取请求uri的结果
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
     * 获取或创建请求追踪标识。
     *
     * @return 获取或创建追踪id的结果
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
