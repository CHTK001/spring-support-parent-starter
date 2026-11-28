// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datapool.support.service.impl;

import com.chua.datasource.support.middleware.MiddlewareNotInstalledException;
import com.chua.datasource.support.middleware.mongodb.MongoCollectionInfo;
import com.chua.datasource.support.middleware.mongodb.MongoDatabaseInfo;
import com.chua.datasource.support.middleware.mongodb.MongoManageProvider;
import com.chua.datasource.support.middleware.mongodb.MongoManageTarget;
import com.chua.datasource.support.middleware.mongodb.MongoQueryResult;
import com.chua.datasource.support.middleware.mongodb.MongoTestResult;
import com.chua.starter.datapool.support.middleware.MongoManageSupport;
import com.chua.starter.datapool.support.model.MongoCollectionVO;
import com.chua.starter.datapool.support.model.MongoDatabaseVO;
import com.chua.starter.datapool.support.model.MongoQueryResultVO;
import com.chua.starter.datapool.support.model.MongoTestVO;
import com.chua.starter.datapool.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datapool.support.model.dto.MongoDocumentDTO;
import com.chua.starter.datapool.support.model.dto.MongoQueryDTO;
import com.chua.starter.datapool.support.service.MongoManageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MongoDB 管理实现：本模块只做「数据源行 → 连接目标 → VO」的编排，
 * 具体探测、浏览与只读查询由 {@link MongoManageProvider}
 * （{@code utils-support-mongodb-starter} 或同等的 OSGi bundle）承担，
 * 因此数据源中心不携带 MongoDB 官方驱动。
 *
 * <p>远端失败的口径：数据源未登记、语句不合法（{@link IllegalArgumentException}）原样透出；
 * 驱动异常已由实现方翻译成可读中文原因（{@link IllegalStateException}），
 * 控制层转成业务错误码。实现未安装时提示「功能未开放」。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoManageServiceImpl implements MongoManageService {

    /**
     * Mongo 管理面取用入口
     */
    private final MongoManageSupport support;

    @Override
    public MongoTestVO test(MiddlewareTestDTO request) {
        MongoManageProvider provider;
        MongoManageTarget target;
        try {
            provider = support.provider();
            target = support.testTarget(request);
        } catch (IllegalStateException | MiddlewareNotInstalledException e) {
            return new MongoTestVO(false, null, e.getMessage());
        }
        MongoTestResult result = provider.test(target);
        String version = result.version() == null || result.version().isEmpty() ? null : result.version();
        return new MongoTestVO(Boolean.TRUE.equals(result.connected()), version, result.message());
    }

    @Override
    public List<MongoDatabaseVO> databases(Long datasourceId) {
        List<MongoDatabaseInfo> rows = support.provider().databases(support.target(datasourceId));
        List<MongoDatabaseVO> result = new ArrayList<>(rows.size());
        for (MongoDatabaseInfo row : rows) {
            result.add(new MongoDatabaseVO(row.name(), row.sizeOnDisk(), row.empty()));
        }
        return result;
    }

    @Override
    public List<MongoCollectionVO> collections(Long datasourceId, String database) {
        String db = database == null || database.isBlank() ? null : database.trim();
        if (db == null) {
            throw new IllegalArgumentException("逻辑库名不能为空");
        }
        List<MongoCollectionInfo> rows = support.provider().collections(support.target(datasourceId), db);
        List<MongoCollectionVO> result = new ArrayList<>(rows.size());
        for (MongoCollectionInfo row : rows) {
            result.add(new MongoCollectionVO(row.name(), row.type(), row.estimatedCount()));
        }
        return result;
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
        MongoQueryResult result = guard("执行查询", () ->
                support.provider().query(support.target(datasourceId), database, expression));
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
                support.provider().document(support.target(request.datasourceId()),
                        request.database(), collection, id));
        if (doc == null) {
            throw new IllegalStateException("文档不存在: " + id);
        }
        return doc;
    }

    /**
     * 业务守卫：语句不合法原样透出，能力包未安装也原样透出（控制层转「功能未开放」）。
     *
     * @param action   操作描述
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 业务结果
     */
    private <T> T guard(String action, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (MiddlewareNotInstalledException | IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[mongodb] {}失败", action, e);
            throw new IllegalStateException(action + "失败: " + e.getMessage(), e);
        }
    }
}
