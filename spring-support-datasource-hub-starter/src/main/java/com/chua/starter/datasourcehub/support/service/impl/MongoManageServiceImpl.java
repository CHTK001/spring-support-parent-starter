// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.service.impl;

import com.chua.starter.datasourcehub.support.middleware.MongoManageClient;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.MongoCollectionVO;
import com.chua.starter.datasourcehub.support.model.MongoDatabaseVO;
import com.chua.starter.datasourcehub.support.model.MongoQueryResultVO;
import com.chua.starter.datasourcehub.support.model.MongoTestVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.model.dto.MongoDocumentDTO;
import com.chua.starter.datasourcehub.support.model.dto.MongoQueryDTO;
import com.chua.starter.datasourcehub.support.service.MongoManageService;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MongoDB 管理实现：探测、逻辑库/集合浏览、只读查询与单文档查看。
 *
 * <p>远端失败的口径：数据源未登记、语句不合法（{@link IllegalArgumentException}）原样透出；
 * 驱动异常统一换成可读中文原因（认证失败/连接超时/权限不足）后以
 * {@link IllegalStateException} 抛出，由控制层转成业务错误码。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoManageServiceImpl implements MongoManageService {

    /**
     * 可读原因的最大长度
     */
    private static final int MESSAGE_LIMIT = 200;

    private final MongoManageClient client;

    @Override
    public MongoTestVO test(MiddlewareTestDTO request) {
        MongoManageClient.Target target;
        try {
            target = testTarget(request);
        } catch (IllegalStateException e) {
            return new MongoTestVO(false, null, e.getMessage());
        }
        try {
            MongoManageClient.Server server = client.probeServer(target);
            String version = server.version() == null || server.version().isEmpty() ? null : server.version();
            return new MongoTestVO(true, version, "连接成功");
        } catch (MongoException e) {
            return new MongoTestVO(false, null, readableMessage(e));
        }
    }

    @Override
    public List<MongoDatabaseVO> databases(Long datasourceId) {
        return guard("读取逻辑库列表", () -> {
            List<MongoDatabaseVO> result = new ArrayList<>();
            for (org.bson.Document row : client.databases(client.target(datasourceId))) {
                result.add(new MongoDatabaseVO(
                        text(row.get("name")),
                        row.get("sizeOnDisk") instanceof Number n ? n.longValue() : null,
                        row.get("empty") instanceof Boolean b ? b : null));
            }
            return result;
        });
    }

    @Override
    public List<MongoCollectionVO> collections(Long datasourceId, String database) {
        String db = database == null || database.isBlank() ? null : database.trim();
        if (db == null) {
            throw new IllegalArgumentException("逻辑库名不能为空");
        }
        return guard("读取集合列表", () -> {
            List<MongoCollectionVO> result = new ArrayList<>();
            for (org.bson.Document row : client.collections(client.target(datasourceId), db)) {
                result.add(new MongoCollectionVO(
                        text(row.get("name")),
                        text(row.get("type")),
                        row.get("estimatedCount") instanceof Number n ? n.longValue() : null));
            }
            return result;
        });
    }

    @Override
    public MongoQueryResultVO query(MongoQueryDTO request) {
        String expression = request == null || request.expression() == null || request.expression().isBlank()
                ? null : request.expression().trim();
        if (expression == null) {
            throw new IllegalArgumentException("查询语句不能为空");
        }
        Long datasourceId = request.datasourceId();
        String database = request.database();
        long start = System.nanoTime();
        MongoManageClient.Result result = guard("执行查询", () ->
                client.run(client.target(datasourceId), database, expression));
        long elapsed = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
        return new MongoQueryResultVO(expression, elapsed, result.rows(),
                result.rows().size(), result.totalRows(), result.limited());
    }

    @Override
    public Map<String, Object> document(MongoDocumentDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("查看请求不能为空");
        }
        String collection = request.collection() == null ? "" : request.collection().trim();
        String id = request.id() == null ? "" : request.id().trim();
        if (collection.isEmpty()) {
            throw new IllegalArgumentException("集合名不能为空");
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("文档 ID 不能为空");
        }
        Map<String, Object> doc = guard("读取文档", () ->
                client.document(client.target(request.datasourceId()), request.database(), collection, id));
        if (doc == null) {
            throw new IllegalStateException("文档不存在: " + id);
        }
        return doc;
    }

    /**
     * 解析测试目标：优先库中数据源，其次即席填写的地址，最后落到第一个 mongodb 数据源。
     *
     * @param request 测试请求，可为空
     * @return 访问目标
     */
    private MongoManageClient.Target testTarget(MiddlewareTestDTO request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            return client.target(request == null ? null : request.datasourceId());
        }
        if (request.datasourceId() != null && request.datasourceId() > 0L) {
            return client.target(request.datasourceId());
        }
        // 即席探测：contextPath 借道承载默认逻辑库名（与 Neo4j 口径一致）
        DataSourceVO adhoc = new DataSourceVO(null, "ad-hoc", MongoManageClient.DS_TYPE, request.host().trim(),
                request.port(), request.contextPath(), request.username(), request.password(), null, 1, null, null, null,
                null, null, null, null, null, null, null, null);
        return client.target(adhoc);
    }

    /**
     * 业务守卫：数据源未登记/语句不合法原样透出，驱动异常换成可读原因。
     *
     * @param action   操作描述
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 业务结果
     */
    private <T> T guard(String action, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (MongoException e) {
            throw new IllegalStateException(action + "失败: " + readableMessage(e), e);
        }
    }

    /**
     * 驱动异常 → 可读中文原因。
     *
     * @param e 驱动异常
     * @return 原因文本
     */
    private String readableMessage(Throwable e) {
        if (e instanceof MongoTimeoutException) {
            return "连接超时：地址不可达或凭据错误（5 秒内未选到主节点）";
        }
        if (e instanceof MongoCommandException command) {
            int code = command.getErrorCode();
            if (code == 18) {
                return "认证失败：用户名或密码错误";
            }
            if (code == 13) {
                return "权限不足：当前用户无权执行该操作";
            }
            return firstLine(command.getErrorMessage());
        }
        if (e instanceof MongoSecurityException) {
            return "认证失败: " + firstLine(e.getMessage());
        }
        if (e instanceof MongoSocketException) {
            return "连接失败: " + firstLine(e.getMessage());
        }
        return firstLine(e.getMessage());
    }

    /**
     * 取消息首行并截断。
     *
     * @param message 原始消息
     * @return 首行截断结果
     */
    private String firstLine(String message) {
        if (message == null || message.isEmpty()) {
            return "未知错误";
        }
        String line = message.split("\n")[0].trim();
        return line.length() > MESSAGE_LIMIT ? line.substring(0, MESSAGE_LIMIT) + "…" : line;
    }

    /**
     * 值转非空文本。
     *
     * @param value 原始值
     * @return 文本，null 为空串
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}