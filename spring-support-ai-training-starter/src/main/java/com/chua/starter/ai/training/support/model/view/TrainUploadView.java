package com.chua.starter.ai.training.support.model.view;

import java.io.Serializable;

/**
 * 训练数据上传结果视图。
 *
 * @param taskId   训练任务标识
 * @param fileName 上传的压缩原始文件名
 * @param dataPath 解压后的数据目录路径
 * @author CH
 * @since 4.0.0.42
 */
public record TrainUploadView(
        String taskId,
        String fileName,
        String dataPath) implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid
}
