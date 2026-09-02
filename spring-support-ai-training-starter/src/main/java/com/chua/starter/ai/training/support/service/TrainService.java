package com.chua.starter.ai.training.support.service;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.starter.ai.training.support.domain.TrainTask;

import java.util.List;

/**
 * 训练服务接口：任务创建、异步训练启动、进度查询。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface TrainService {

    /**
     * 创建训练任务（尚未启动）。
     *
     * @param originalFileName 上传文件名
     * @param dataPath         解压后的数据目录路径
     * @return 创建的任务
     */
    TrainTask createTask(String originalFileName, String dataPath);

    /**
     * 启动异步训练（后台线程执行 DL4J 训练）。
     *
     * @param taskId 任务 ID
     * @throws Exception 训练启动失败（任务不存在或已在运行）
     */
    void startTraining(String taskId) throws Exception;

    /**
     * 获取任务状态。
     *
     * @param taskId 任务 ID
     * @return 任务详情（含进度）
     */
    TrainTask getTask(String taskId);

    /**
     * 列出所有训练任务。
     *
     * @return 任务列表
     */
    List<TrainTask> listTasks();

    /**
     * 更新任务的超参数配置（仅 PENDING 状态可修改）。
     *
     * @param taskId   任务 ID
     * @param argument 新的超参数
     */
    void updateArgument(String taskId, TrainArgument argument);
}