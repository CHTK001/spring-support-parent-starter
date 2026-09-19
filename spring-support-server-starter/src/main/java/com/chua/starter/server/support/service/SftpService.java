package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.TempFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * SFTP 文件管理服务
 * <p>
 * 基于主机绑定的身份凭据对远程文件系统执行浏览、读写与结构操作。
 * 与终端通道解耦，全部经 REST 实现（区别于 guacd 文件流方案）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SftpService {

    /**
     * 列出目录内容
     *
     * @param hostId     主机 标识
     * @param identityId 指定身份 标识，可为空取默认绑定身份
     * @param path       目录路径，空时取用户主目录
     * @return 文件条目列表（name/path/directory/file/size 等键）
     */
    ReturnResult<List<Map<String, Object>>> list(Long hostId, Long identityId, String path);

    /**
     * 读取文本文件内容
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param path       文件路径
     * @return 内容结果：content（可能被截断）/ truncated / 大小
     */
    ReturnResult<Map<String, Object>> readContent(Long hostId, Long identityId, String path);

    /**
     * 写入文本文件内容（整文件覆盖）
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param path       文件路径
     * @param content    新内容
     * @return 结果
     */
    ReturnResult<Void> writeContent(Long hostId, Long identityId, String path, String content);

    /**
     * 上传文件到指定目录
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param directory  目标目录
     * @param file       上传的文件
     * @return 结果（含远端完整路径）
     */
    ReturnResult<String> upload(Long hostId, Long identityId, String directory, MultipartFile file);

    /**
     * 下载远端文件到本地临时文件
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param path       远端文件路径
     * @return 临时文件句柄（使用方负责 关闭 删除）
     */
    ReturnResult<TempFile> download(Long hostId, Long identityId, String path);

    /**
     * 创建目录
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param path       新目录路径
     * @return 结果
     */
    ReturnResult<Void> mkdir(Long hostId, Long identityId, String path);

    /**
     * 删除文件或目录
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param path       路径
     * @param recursive  目录是否递归删除
     * @return 结果
     */
    ReturnResult<Void> delete(Long hostId, Long identityId, String path, boolean recursive);

    /**
     * 重命名或移动
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param fromPath   原路径
     * @param toPath     新路径
     * @return 结果
     */
    ReturnResult<Void> rename(Long hostId, Long identityId, String fromPath, String toPath);
}
