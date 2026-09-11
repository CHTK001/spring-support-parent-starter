package com.chua.starter.project.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.entity.MsProjectDeployLog;
import com.chua.starter.project.support.entity.MsProjectUploadArchive;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 发布操作服务（部署 / 升级 / 启停）
 * <p>
 * 基于 SSH/SFTP 对目标服务器执行远程部署、升级、启动、停止与重启，
 * 并写入 {@code ms_project_deploy_log} 部署日志。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DeployService {

    /**
     * 部署或升级项目
     * <p>
     * 根据项目升级策略（是否先删除旧内容/覆盖模式/是否自动解压）执行：
     * 上传部署包 → 可选清除部署目录 → 可选解压到部署目录 → 记录部署日志。
     * 多目标服务器并行执行。
     * </p>
     *
     * @param projectId  项目 ID
     * @param action     操作类型 DEPLOY / UPGRADE
     * @param file       部署包文件（zip/tar.gz/jar 等）
     * @return 每台主机的部署日志
     */
    ReturnResult<List<MsProjectDeployLog>> deploy(Long projectId, String action, MultipartFile file);

    /**
     * 启动项目（执行启动脚本）
     *
     * @param projectId 项目 ID
     * @return 每台主机的执行日志
     */
    ReturnResult<List<MsProjectDeployLog>> start(Long projectId);

    /**
     * 停止项目（执行停止脚本）
     *
     * @param projectId 项目 ID
     * @return 每台主机的执行日志
     */
    ReturnResult<List<MsProjectDeployLog>> stop(Long projectId);

    /**
     * 重启项目（执行重启脚本）
     *
     * @param projectId 项目 ID
     * @return 每台主机的执行日志
     */
    ReturnResult<List<MsProjectDeployLog>> restart(Long projectId);

    /**
     * 部署日志列表
     *
     * @param projectId 项目 ID，可为空查询全部
     * @param action    操作类型，可为空
     * @return 日志列表（最新在前，上限 200）
     */
    ReturnResult<List<MsProjectDeployLog>> listLogs(Long projectId, String action);

    /**
     * 部署包归档列表（资产维度历史版本）
     *
     * @param projectId 项目 ID
     * @param hostId    主机 ID，可为空
     * @return 归档记录（版本去重后倒序，供回滚选择）
     */
    ReturnResult<List<MsProjectUploadArchive>> listArchives(Long projectId, Long hostId);

    /**
     * 回滚项目到指定历史版本
     * <p>
     * 从归档存储取出目标版本部署包，按项目绑定主机并发重走部署流程
     * （清目录 → 上传 → 可选解压），失败主机单独标记。
     * </p>
     *
     * @param projectId 项目 ID
     * @param archiveId 归档记录 ID（指定版本来源）
     * @return 每台主机的部署日志
     */
    ReturnResult<List<MsProjectDeployLog>> rollback(Long projectId, Long archiveId);

    /**
     * 实时查看项目日志（tail）
     * <p>
     * 基于项目配置的日志目录与文件名，SSH 执行 {@code tail} 读取最近内容。
     * cursor 不为空时返回从该字节偏移起的增量内容（增量轮询）。
     * </p>
     *
     * @param projectId 项目 ID
     * @param hostId    目标主机 ID
     * @param cursor    上次读取的字节偏移，可为空
     * @param lines     拉取行数，默认 500
     * @return 日志内容（content / cursor / eof）
     */
    ReturnResult<Map<String, Object>> tailLog(Long projectId, Long hostId, Long cursor, Integer lines);
}