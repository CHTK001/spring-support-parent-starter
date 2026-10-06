package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import java.util.List;

/**
 * 中间件数据源取数：把「按 ID 取行并校验类型、缺省时挑第一个同类型行」收敛到一处。
 *
 * <p>每个中间件客户端都需要这条规则，各自复制一份会把口径差异藏起来（例如早期
 * ZooKeeper 允许 {@code zk} 别名而其它类型不允许），因此统一由本类按别名列表判定。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
final class MiddlewareDatasources {

    private MiddlewareDatasources() {
    }

    /**
     * 取指定（或第一个匹配类型的）数据源。
     *
     * @param service      数据源服务
     * @param datasourceId 数据源 ID，为空或非正数时按类型挑第一个
     * @param types        该中间件可接受的数据源类型（含别名），忽略大小写
     * @return 数据源配置
     * @throws IllegalStateException 数据源不存在、类型不符或尚未登记
     */
    static DataSourceVO require(DatasourceService service, Long datasourceId, String... types) {
        if (datasourceId != null && datasourceId > 0L) {
            DataSourceVO ds = unwrap(service.get(datasourceId));
            if (!matches(ds, types)) {
                throw new IllegalStateException("数据源 #" + datasourceId + " 是 " + ds.type() + " 类型，无法用于 "
                        + types[0] + " 管理");
            }
            return ds;
        }
        List<DataSourceVO> all = unwrap(service.list());
        if (all != null) {
            for (DataSourceVO ds : all) {
                if (matches(ds, types)) {
                    return ds;
                }
            }
        }
        throw new IllegalStateException("尚未登记 " + types[0] + " 数据源，请先在「数据源管理」新增");
    }

    /**
     * 取出成功响应的数据。
     *
     * @param result 服务响应
     * @param <T>    数据类型
     * @return 数据
     * @throws IllegalStateException 响应失败
     */
    static <T> T unwrap(ReturnResult<T> result) {
        if (result == null) {
            throw new IllegalStateException("数据源服务无响应");
        }
        if (!result.isOk()) {
            throw new IllegalStateException(result.getMsg() == null ? "数据源读取失败" : result.getMsg());
        }
        return result.getData();
    }

    /**
     * 数据源类型是否命中候选类型之一。
     *
     * @param ds    数据源配置
     * @param types 候选类型
     * @return 命中为真
     */
    private static boolean matches(DataSourceVO ds, String... types) {
        String type = ds.type() == null ? "" : ds.type().trim();
        for (String candidate : types) {
            if (candidate.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }
}
