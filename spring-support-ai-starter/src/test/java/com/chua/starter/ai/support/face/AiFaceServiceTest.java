package com.chua.starter.ai.support.face;

import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceIdentify;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.service.impl.AiFaceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiFaceService 单元测试 (Mock FaceClientManager)。
 *
 * <p>覆盖 AiFaceServiceImpl 的委托行为:
 * detect/detectLargest/detectPipeline 委托给 FacePipeline,
 * extractFeature 委托给 FeatureExtractor,
 * 验证方法被正确调用且返回值正常透传。</p>
 *
 * <p>完整端到端测试 (含 ONNX 模型) 需要在本地有 ONNX 模型文件,
 * 本测试只针对无模型依赖的纯委托逻辑。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("AiFaceService 单元测试 (Mock)")
@ExtendWith(MockitoExtension.class)
class AiFaceServiceTest {

    @Mock
    private FaceClientManager faceClientManager;

    @Mock
    private FacePipeline facePipeline;

    @Mock
    private FeatureExtractor featureExtractor;

    @InjectMocks
    private AiFaceServiceImpl aiFaceService;

    @Test
    @DisplayName("detect: 应委托给 FacePipeline.detect 并返回检测结果")
    void shouldDelegateDetectToFacePipeline() {
        FaceDetectionHit hit = new FaceDetectionHit(null, null, true, 1.0f);
        when(faceClientManager.currentPipeline()).thenReturn(facePipeline);
        when(facePipeline.detect(any(byte[].class))).thenReturn(List.of(hit));

        List<FaceDetectionHit> result = aiFaceService.detect(new byte[]{1, 2, 3});

        assertThat(result).containsExactly(hit);
        verify(faceClientManager, times(1)).currentPipeline();
        verify(facePipeline, times(1)).detect(any(byte[].class));
    }

    @Test
    @DisplayName("detectLargest: 应委托给 FacePipeline.detectLargest")
    void shouldDelegateDetectLargest() {
        FaceDetectionHit hit = new FaceDetectionHit(null, null, true, 1.0f);
        when(faceClientManager.currentPipeline()).thenReturn(facePipeline);
        when(facePipeline.detectLargest(any(byte[].class))).thenReturn(hit);

        FaceDetectionHit result = aiFaceService.detectLargest(new byte[]{1});

        assertThat(result).isEqualTo(hit);
        verify(facePipeline).detectLargest(any(byte[].class));
    }

    @Test
    @DisplayName("detectPipeline: 应复用 currentPipeline().detect() 返回结果")
    void shouldDelegateDetectPipeline() {
        FaceDetectionHit hit = new FaceDetectionHit(null, null, true, 1.0f);
        when(faceClientManager.currentPipeline()).thenReturn(facePipeline);
        when(facePipeline.detect(any(byte[].class))).thenReturn(List.of(hit));

        List<FaceDetectionHit> result = aiFaceService.detectPipeline(new byte[]{1});

        assertThat(result).containsExactly(hit);
        // detectPipeline 内部走 currentPipeline().detect(), 无独立方法
        verify(facePipeline, times(1)).detect(any(byte[].class));
    }

    @Test
    @DisplayName("extractFeature: 应委托给 FeatureExtractor.extract")
    void shouldDelegateExtractFeature() {
        float[] feature = new float[]{0.1f, 0.2f, 0.3f};
        when(faceClientManager.currentFeatureExtractor()).thenReturn(featureExtractor);
        when(featureExtractor.extract(any(byte[].class))).thenReturn(feature);

        float[] result = aiFaceService.extractFeature(new byte[]{1});

        assertThat(result).isSameAs(feature);
        verify(faceClientManager).currentFeatureExtractor();
        verify(featureExtractor).extract(any(byte[].class));
    }

    @Test
    @DisplayName("extractFeature: 当返回 null 时, 透传 null 不抛异常")
    void shouldTolerateNullFeature() {
        when(faceClientManager.currentFeatureExtractor()).thenReturn(featureExtractor);
        when(featureExtractor.extract(any(byte[].class))).thenReturn(null);

        float[] result = aiFaceService.extractFeature(new byte[]{1});

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("detect: 当 pipeline 返回空列表时, 应返回空列表")
    void shouldReturnEmptyListWhenPipelineReturnsEmpty() {
        when(faceClientManager.currentPipeline()).thenReturn(facePipeline);
        when(facePipeline.detect(any(byte[].class))).thenReturn(List.of());

        List<FaceDetectionHit> result = aiFaceService.detect(new byte[]{1});

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("compareFeature: 1:1 特征比对, 应委托给 extractFeature 后做余弦相似度")
    void shouldDelegateCompareFeature() {
        float[] a = new float[]{1.0f, 0.0f, 0.0f};
        float[] b = new float[]{1.0f, 0.0f, 0.0f};
        when(faceClientManager.currentFeatureExtractor()).thenReturn(featureExtractor);
        when(featureExtractor.extract(any(byte[].class))).thenReturn(a, b);

        double score = aiFaceService.compare(new byte[]{1}, new byte[]{2});

        // 两个相同的单位向量, 相似度应接近 1.0
        assertThat(score).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        // compare 内部两次调用 extractFeature
        verify(featureExtractor, times(2)).extract(any(byte[].class));
    }

    @Test
    @DisplayName("compareFeature: 正交向量应为 0")
    void shouldReturnZeroForOrthogonalVectors() {
        float[] a = new float[]{1.0f, 0.0f, 0.0f};
        float[] b = new float[]{0.0f, 1.0f, 0.0f};

        double score = aiFaceService.compareFeature(a, b);

        assertThat(score).isEqualTo(0.0);
        // 直接传特征向量不走 extractFeature
        verify(featureExtractor, never()).extract(any(byte[].class));
    }

    @Test
    @DisplayName("detect: 不应重复调用 currentPipeline (单次调用)")
    void shouldNotRecreatePipelineMultipleTimes() {
        when(faceClientManager.currentPipeline()).thenReturn(facePipeline);
        when(facePipeline.detect(any(byte[].class))).thenReturn(List.of());

        aiFaceService.detect(new byte[]{1});
        aiFaceService.detect(new byte[]{2});

        // 两次 detect 调用, currentPipeline 应该被调用 2 次 (无缓存)
        verify(faceClientManager, times(2)).currentPipeline();
    }
}