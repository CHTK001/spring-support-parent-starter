package com.chua.starter.ai.training.support.model.view;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 训练数据上传结果视图。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder
public class TrainUploadView implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
      * 训练任务 标识。
     */
    private String taskId;

    /**
      * 上传的 压缩 原始文件名。
     */
    private String fileName;

    /**
     * 解压后的数据目录路径。
     */
    private String dataPath;
}