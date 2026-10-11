package com.chua.starter.ai.support.service;

/**
 * 人脸登记时检出多张人脸而被拒绝。
 *
 * <p>底库的语义是「一个 {@code id} = 一张脸 = 一个人」。底层特征管线只会取面积
 * 最大的那张脸出特征，所以一张合影实际只会入库其中一个人，但接口过去仍返回
 * 「登记成功」——用户以为全家都录进去了，直到 1:N 检索认不出人才发现少了人，
 * 且此时数据已经脏了（档案与向量对不上）。</p>
 *
 * <p>因此在写入任何数据之前就拒绝，并把这个可预期的业务分支与真正的系统故障
 * 分开：控制器只需单独捕获它给出明确提示，不必打 ERROR 全栈。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public class MultiFaceEnrollException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 检出的总人脸数。
     */
    private final int faceCount;

    /**
     * 构造异常。
     *
     * @param faceCount 检出的总人脸数（至少为 2）
     */
    public MultiFaceEnrollException(int faceCount) {
        super("人脸底库仅支持单人脸登记，检测到 " + faceCount + " 张人脸，请裁剪到只含目标人脸后重试");
        this.faceCount = faceCount;
    }

    /**
     * 检出的总人脸数。
     *
     * @return 至少为 2 的脸数
     */
    public int getFaceCount() {
        return faceCount;
    }
}