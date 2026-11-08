package com.chua.starter.httpman.service;

import com.chua.starter.httpman.entity.MsHttpmanCollection;
import com.chua.starter.httpman.entity.MsHttpmanRequest;
import com.chua.starter.httpman.entity.MsHttpmanVariable;
import com.chua.starter.httpman.model.dto.HttpmanHistoryQuery;
import com.chua.starter.httpman.model.dto.HttpmanRequestSave;
import com.chua.starter.httpman.model.dto.HttpmanVariableSave;
import com.chua.starter.mybatis.entity.PageResult;
import com.chua.common.support.lang.code.ReturnResult;

import java.util.List;
import java.util.Map;

/**
 * 接口调试控制台 - 服务
 *
 * <p>提供集合/请求/历史/变量的 CRUD 以及请求代理执行能力。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface HttpmanService {

    /**
     * 创建集合
     *
     * @param collection 集合实体
     * @return 集合实体
     */
    MsHttpmanCollection createCollection(MsHttpmanCollection collection);

    /**
     * 修改集合
     *
     * @param collection 集合实体
     * @return 集合实体
     */
    MsHttpmanCollection updateCollection(MsHttpmanCollection collection);

    /**
     * 删除集合（级联删除其下请求及历史）
     *
     * @param id 集合标识
     */
    void deleteCollection(String id);

    /**
     * 查询集合树（根节点为 null 时表示从根开始）
     *
     * @return 集合树列表
     */
    List<MsHttpmanCollection> listCollections();

    /**
     * 创建/修改请求
     *
     * @param save 请求保存参数
     * @return 请求实体
     */
    MsHttpmanRequest saveRequest(HttpmanRequestSave save);

    /**
     * 修改请求
     *
     * @param id 请求标识
     * @param save 请求保存参数
     * @return 请求实体
     */
    MsHttpmanRequest updateRequest(String id, HttpmanRequestSave save);

    /**
     * 删除请求
     *
     * @param id 请求标识
     */
    void deleteRequest(String id);

    /**
     * 获取请求详情
     *
     * @param id 请求标识
     * @return 请求实体
     */
    MsHttpmanRequest getRequest(String id);

    /**
     * 查询集合内的请求列表
     *
     * @param collectionId 集合标识
     * @param parentId     父级标识，NULL 时表示集合根
     * @return 请求列表
     */
    List<MsHttpmanRequest> listRequests(String collectionId, String parentId);

    /**
     * 执行请求（代理），返回完整响应，不写历史
     *
     * @param request 请求实体（已含 URL/头/正文）
     * @return 执行结果 {status, headers, body, costMs, error}
     */
    ReturnResult<Map<String, Object>> execute(MsHttpmanRequest request);

    /**
     * 执行请求（代理），写历史记录
     *
     * @param request 请求实体（已含 URL/头等）
     * @param collectionId 所属集合标识
     * @return 执行结果 {historyId, status, headers, body, costMs, error}
     */
    ReturnResult<Map<String, Object>> executeAndRecord(MsHttpmanRequest request, String collectionId);

    /**
     * 分页查询历史
     *
     * @param query 查询参数
     * @return 历史分页
     */
    PageResult<Map<String, Object>> pageHistory(HttpmanHistoryQuery query);

    /**
     * 查询历史详情
     *
     * @param id 历史标识
     * @return 历史详情（含完整请求/响应）
     */
    Map<String, Object> getHistory(String id);

    /**
     * 删除历史
     *
     * @param id 历史标识
     */
    void deleteHistory(String id);

    /**
     * 清理过期历史
     *
     * @param retentionDays 保留天数，0 表示不限制
     */
    void cleanHistory(int retentionDays);

    /**
     * 创建变量
     *
     * @param variable 变量实体
     * @return 变量实体
     */
    MsHttpmanVariable createVariable(MsHttpmanVariable variable);

    /**
     * 修改变量
     *
     * @param variable 变量实体
     * @return 变量实体
     */
    MsHttpmanVariable updateVariable(MsHttpmanVariable variable);

    /**
     * 获取变量详情
     *
     * @param id 变量标识
     * @return 变量实体
     */
    MsHttpmanVariable getVariable(String id);

    /**
     * 删除变量
     *
     * @param id 变量标识
     */
    void deleteVariable(String id);

    /**
     * 查询变量列表
     *
     * @param scope        作用域
     * @param collectionId 集合标识
     * @return 变量列表
     */
    List<MsHttpmanVariable> listVariables(String scope, String collectionId);

    /**
     * 按作用域获取变量映射（用于 {{variable}} 模板解析）
     *
     * @param scope        作用域
     * @param collectionId 集合标识
     * @return 变量映射
     */
    Map<String, String> getVariableMap(String scope, String collectionId);
}
