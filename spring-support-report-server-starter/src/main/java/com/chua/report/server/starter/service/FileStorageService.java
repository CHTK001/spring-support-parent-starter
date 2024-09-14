package com.chua.report.server.starter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.oss.result.ListObjectResult;
import com.chua.report.server.starter.entity.FileStorage;

import java.util.Set;

/**
 * 文件存储服务接口。
 * 该接口定义了对文件存储对象进行增、删、改的基本操作，实现了IService接口，特定于FileStorage对象。
 * @author CH
 * @since 2024/7/22
 */
public interface FileStorageService extends IService<FileStorage> {

    /**
     * 删除文件存储项。
     * 使用集合形式的ID参数以支持批量删除操作。
     * @param ids 待删除文件的ID集合。
     * @return 删除操作的成功与否。
     */
    Boolean deleteFor(Set<String> ids);

    /**
     * 更新文件存储项。
     * 提供完整的FileStorage对象以更新数据库中的相应记录。
     * @param t 待更新的FileStorage对象。
     * @return 更新操作的成功与否。
     */
    Boolean updateFor(FileStorage t);

    /**
     * 保存文件存储项。
     * 对于新文件存储项，这将执行插入操作；对于已存在ID的文件存储项，这将执行更新操作。
     * @param t 待保存的FileStorage对象。
     * @return 保存操作的成功与否。
     */
    Boolean saveFor(FileStorage t);

    /**
     * 分页查询文件存储项。
     * 提供文件存储ID和分页请求参数，用于分页查询数据库中的文件存储记录。
     *
     * @param fileStorageId 文件存储ID。
     * @param path          文件存储路径。
     * @param limit         每页记录数。
     * @param marker        分页标记。
     * @return 分页查询结果。
     */
    ListObjectResult viewer(Integer fileStorageId, String path, Integer limit, String marker);
}
