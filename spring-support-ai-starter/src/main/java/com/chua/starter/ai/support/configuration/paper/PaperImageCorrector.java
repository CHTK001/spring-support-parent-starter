package com.chua.starter.ai.support.configuration.paper;

/**
 * 试卷图片矫正契约 —— 在扫描管线入口对拍照/导出图片做四边形检测与透视拉平。
 *
 * <p>业务模块（eduai）只依赖本接口决定「要不要矫正」，不感知模型文件位置、缓存目录
 * 与失败降级细节；实现由 {@code DefaultPaperImageCorrector} 提供。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
public interface PaperImageCorrector {

    /**
     * 矫正图片：检测卡片四边形并拉平为水平矩形图。
     *
     * @param imageData 原始图片字节
     * @return 矫正后的 PNG 字节；未检出卡片或执行失败时返回原图
     */
    byte[] correct(byte[] imageData);
}
