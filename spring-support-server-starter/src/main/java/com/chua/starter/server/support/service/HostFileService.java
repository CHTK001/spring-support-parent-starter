package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.starter.server.support.model.vo.HostFileConfigVO;
import com.chua.starter.server.support.model.vo.HostFileDeleteVO;
import com.chua.starter.server.support.model.vo.HostFileDigestVO;
import com.chua.starter.server.support.model.vo.HostFileEntryVO;
import com.chua.starter.server.support.model.vo.HostFilePageVO;

import java.util.List;

/**
 * 主机文件服务：运行本服务的这台机器上的文件存储。
 *
 * <p>所有 key 都是相对存储根的路径，进入实现前先经
 * {@link com.chua.starter.server.support.util.HostFilePaths} 归一化并挡住
 * 目录穿越。能力差异按存储后端如实返回：只有本地存储能算 MD5、建目录、
 * 重命名目录与给出主机绝对路径；对象存储上的目录操作直接报不支持，
 * 而不是静默失败。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
public interface HostFileService {

    /**
     * 读取存储信息。
     *
     * @param bucket 存储提供者名，留空用默认提供者
     * @return 启用状态、根目录、可切换的提供者
     */
    ReturnResult<HostFileConfigVO> config(String bucket);

    /**
     * 列目录（支持关键字过滤与排序）。
     *
     * @param bucket  存储提供者名，留空用默认提供者
     * @param path    目录 key，留空为根
     * @param keyword 名称关键字（不区分大小写，可空）
     * @param sort    排序字段：name / size / time
     * @param order   排序方向：asc / desc
     * @param limit   每页条数
     * @param offset  跳过条数
     * @return 目录页
     */
    ReturnResult<HostFilePageVO> list(String bucket, String path, String keyword, String sort,
                                      String order, int limit, int offset);

    /**
     * 新建目录。
     *
     * @param bucket 存储提供者名
     * @param path   父目录 key
     * @param name   目录名
     * @return 新目录条目
     */
    ReturnResult<HostFileEntryVO> mkdir(String bucket, String path, String name);

    /**
     * 重命名（同目录改名）。
     *
     * @param bucket 存储提供者名
     * @param key    目标 key
     * @param name   新名称
     * @return 重命名后的条目
     */
    ReturnResult<HostFileEntryVO> rename(String bucket, String key, String name);

    /**
     * 移动到另一个目录。
     *
     * @param bucket    存储提供者名
     * @param key       目标 key
     * @param targetDir 目标目录 key（根目录为空串）
     * @return 移动后的条目
     */
    ReturnResult<HostFileEntryVO> move(String bucket, String key, String targetDir);

    /**
     * 删除文件或目录（目录递归删除）。
     *
     * @param bucket 存储提供者名
     * @param key    目标 key
     * @return 是否删除成功
     */
    ReturnResult<Boolean> delete(String bucket, String key);

    /**
     * 批量删除：逐个执行并逐个回报失败原因。
     *
     * @param bucket 存储提供者名
     * @param keys   目标 key 列表
     * @return 成功 / 失败汇总
     */
    ReturnResult<HostFileDeleteVO> deleteBatch(String bucket, List<String> keys);

    /**
     * 计算文件 MD5（仅本地存储）。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @return 指纹
     */
    ReturnResult<HostFileDigestVO> md5(String bucket, String key);

    /**
     * 打开文件流，供下载与内联预览。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @return 读取结果；不存在时 success=false
     */
    GetObjectResult open(String bucket, String key);

    /**
     * 读取文本类文件的开头，用于在线预览。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @return 文本内容（超长截断并标注）
     */
    ReturnResult<String> readText(String bucket, String key);
}
