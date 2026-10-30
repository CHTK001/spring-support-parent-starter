package com.chua.starter.ai.support.manage.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ONNX 本地模型的统一能力分类器。
 *
 * <p><b>为什么需要它：</b>此前同一件事有三套互不一致的口径 ——
 * {@code ApiProviderController#classifyOnnxTask}（列模型用，含 {@code "age"} 命中
 * {@code "image"} 的误判）、{@code OnnxProviderMaterializer#taskOf}（外部目录落库用，
 * 产出 {@code feature} 这种前端不认的词），以及前端 {@code TASK_TO_TYPE}。
 * 三者不一致导致「管理页看到的一批、默认模型选到的是另一批」。</p>
 *
 * <p>本类作为<b>唯一</b>分类来源，产出前端 {@code TASK_TO_TYPE} /
 * {@code MODEL_SUBTYPE_LABELS} 认得的规范词表：
 * {@code task ∈ {layout, ocr, face_recognition, speech_recognition, speech_synthesis,
 * rerank, image_generation, video_generation, embedding, image_cv}}，
 * {@code subtype} 取 {@code ocr_* / face_* / object_detection / defect_detection /
 * image_classification}。ONNX 与云端提供商在管理面走完全相同的模型结构，
 * 无需前端特例。</p>
 *
 * @author CH
 */
public final class OnnxModelTaxonomy {

    private OnnxModelTaxonomy() {
    }

    /**
     * 规范任务名（前端 {@code TASK_TO_TYPE} 的 key）。
     *
     * @param modelId 模型标识（如 {@code cn-clip-rn50-image}）
     * @return 规范任务名
     */
    public static String task(String modelId) {
        String id = lower(modelId);
        // 布局 / 文档结构
        if (has(id, "layout", "docling", "structure", "table-struct", "pp-doc")) {
            return "layout";
        }
        // OCR / 文本检测识别 / 票据印章 / 文字矫正 / 方向
        if (has(id, "ocr", "paddle", "pp-word", "svtr", "duguang", "seal", "card-correction",
                "text-detect", "qota", "macbert", "corrector", "dbnet", "crnn",
                "orientation", "deskew")) {
            return "ocr";
        }
        // 人脸 / 活体 / 属性 / 关键点 / 换脸 / 人脸超分
        // 注意：**不要**用裸 "age"，否则会命中 "image"（cn-clip-rn50-image 曾被误判成人脸）
        if (has(id, "face", "insightface", "scrfd", "adaface", "arcface", "arc-face",
                "retinaface", "tinaface", "spoof", "genderage", "gender", "emotion",
                "landmark", "deepfake", "gfpgan", "codeformer", "2d106", "3d68")) {
            return "face_recognition";
        }
        // 语音识别 / 声纹 / 说话人
        if (has(id, "whisper", "sensevoice", "voiceprint", "fingerprint", "asr", "speech-rec",
                "dfsmn", "campplus", "wespeaker", "icefall", "kws", "parakeet")) {
            return "speech_recognition";
        }
        // 语音合成
        if (has(id, "tts", "speech-synthesis")) {
            return "speech_synthesis";
        }
        // 重排序
        if (has(id, "rerank")) {
            return "rerank";
        }
        // 文生图（扩散系 / 通义万相系）
        if (has(id, "stable-diffusion", "sd-turbo", "lcm-lora", "anime-gan", "text-to-image",
                "wanx", "wan2", "qwen-image", "imageedit", "image-edit", "text2image", "t2i",
                "dall-e", "cogview")) {
            return "image_generation";
        }
        // 文生视频 / 动作识别
        if (has(id, "text-to-video", "video-generation", "t2v", "vace", "c3d", "action-rec")) {
            return "video_generation";
        }
        // 嵌入 / 图像特征编码器（clip / dino / nima / reid / sam / 手部姿态 等）
        if (has(id, "embedding", "embed", "bge", "minilm", "granite", "wemm", "dino", "clip",
                "siglip", "nima", "osnet", "reid", "handpose", "hand-pose",
                "resnet50-feature", "sam-encoder")) {
            return "embedding";
        }
        // 其余（分类 / 检测 / 分割 / 姿态 / 深度 / 抠图 / 超分 / 描述 / 上色等）归图片识别
        return "image_cv";
    }

    /**
     * 规范细分类型（前端 {@code MODEL_SUBTYPE_LABELS} 的 key）；无细分时返回空串。
     *
     * @param modelId 模型标识
     * @param task    规范任务名（{@link #task(String)} 的结果）
     * @return 细分类型或空串
     */
    public static String subtype(String modelId, String task) {
        String id = lower(modelId);
        if ("ocr".equals(task)) {
            if (has(id, "correction", "corrector", "macbert", "csc")) {
                return "ocr_correction";
            }
            if (has(id, "orientation", "direction", "deskew", "rotat")) {
                return "ocr_direction";
            }
            if (has(id, "restore", "restorer", "super", "upscale")) {
                return "ocr_restore";
            }
            if (has(id, "detector", "-det", "text-detect", "dbnet", "pse")) {
                return "ocr_detection";
            }
            if (has(id, "recognizer", "-rec", "svtr", "crnn")) {
                return "ocr_recognition";
            }
            return "";
        }
        if ("face_recognition".equals(task)) {
            if (has(id, "anime", "cartoon")) {
                return "anime_face_detection";
            }
            if (has(id, "deepfake", "swap")) {
                return "face_deepfake";
            }
            if (has(id, "emotion")) {
                return "face_emotion";
            }
            if (has(id, "genderage", "gender", "attribute")) {
                return "face_attribute";
            }
            if (has(id, "landmark", "keypoint", "2d106", "3d68")) {
                return "face_landmark";
            }
            if (has(id, "liveness", "spoof", "anti-spoof")) {
                return "face_liveness";
            }
            if (has(id, "pose")) {
                return "face_pose";
            }
            if (has(id, "restore", "enhance", "gfpgan", "codeformer", "super")) {
                return "face_restore";
            }
            if (has(id, "feature", "arcface", "arc-face", "adaface", "r50", "w600k", "embedding")) {
                return "face_feature";
            }
            if (has(id, "detect", "detector", "scrfd", "retinaface", "tinaface", "-det")) {
                return "face_detection";
            }
            return "face_recognition";
        }
        if ("image_cv".equals(task) || "detection".equals(task)) {
            if (has(id, "defect", "flaw")) {
                return "defect_detection";
            }
            if (has(id, "classif", "resnet", "efficientnet", "mobilenet")) {
                return "image_classification";
            }
            if (has(id, "detect", "detector", "yolo")) {
                return "object_detection";
            }
            return "";
        }
        return "";
    }

    /**
     * 组装 capability 条目（管理面与云提供商同构）。
     *
     * @param modelId 模型标识
     * @return capability（含 task / subtype / traits / protocol 等）
     */
    public static Map<String, Object> capability(String modelId) {
        String task = task(modelId);
        String sub = subtype(modelId, task);
        Map<String, Object> cap = new LinkedHashMap<>(8);
        cap.put("task", task);
        if (!sub.isBlank()) {
            cap.put("subtype", sub);
        }
        cap.put("traits", traits(task));
        cap.put("protocol", "onnx");
        cap.put("connection_role", "user");
        cap.put("allow_cross_origin_credentials", false);
        cap.put("provider_params", null);
        return cap;
    }

    /**
     * 依据 task 返回输入 / 输出能力（供模型卡片图标展示）。
     *
     * @param task 规范任务名
     * @return traits 列表
     */
    public static List<String> traits(String task) {
        return switch (task == null ? "" : task) {
            case "embedding" -> List.of("input:text", "output:vector");
            case "speech_recognition" -> List.of("input:audio", "output:text");
            case "speech_synthesis" -> List.of("input:text", "output:audio");
            case "image_generation" -> List.of("input:text", "output:image");
            case "video_generation" -> List.of("input:text", "output:video");
            default -> List.of("input:image", "output:text");
        };
    }

    /**
     * lower
     * @param modelId 字符串
     * @return 返回值
    */
    private static String lower(String modelId) {
        return String.valueOf(modelId == null ? "" : modelId).toLowerCase();
    }

    /**
     * has
     * @param id 字符串
     * @param tokens 参数
     * @return 返回值
    */
    private static boolean has(String id, String... tokens) {
        for (String t : tokens) {
            if (id.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
