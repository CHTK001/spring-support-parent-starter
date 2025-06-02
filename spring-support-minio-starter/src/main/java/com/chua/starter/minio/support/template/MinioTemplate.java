package com.chua.starter.minio.support.template;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.minio.support.properties.MinioProperties;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.springframework.beans.factory.InitializingBean;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.chua.common.support.constant.CommonConstant.SYMBOL_LEFT_SLASH;

/**
 * minio
 *
 * @author CH
 */
public class MinioTemplate implements InitializingBean {

    private static final Log log = Log.getLogger(MinioClient.class);
    private final MinioProperties minioProperties;
    private MinioClient minioClient;

    public MinioTemplate(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.minioClient = PearlMinioClient.builder()
                .endpoint(minioProperties.getAddress())
                .credentials(minioProperties.getUsername(), minioProperties.getPassword())
                .build();
    }

    /**
     * 查询所有存储桶
     *
     * @return Bucket 集合
     */
    @SneakyThrows
    public List<Bucket> listBuckets() {
        return minioClient.listBuckets();
    }

    /**
     * 桶是否存在
     *
     * @param bucketName 桶名
     * @return 是否存在
     */
    @SneakyThrows
    public boolean bucketExists(String bucketName) {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void makeBucket(String bucketName) {
        if (!bucketExists(bucketName)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 删除一个空桶 如果存储桶存在对象不为空时，删除会报错。
     *
     * @param bucketName 桶名
     */
    @SneakyThrows
    public void removeBucket(String bucketName) {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }

    /**
     * 上传文件
     *
     * @param inputStream      流
     * @param originalFileName 原始文件名
     * @param bucketName       桶名
     * @return OssFile
     */
    @SneakyThrows
    public void putObject(InputStream inputStream, String bucketName, String originalFileName) {
        String uuidFileName = generateOssUuidFileName(originalFileName);
        try (InputStream is = inputStream) {
            if (StringUtils.isEmpty(bucketName)) {
                bucketName = minioProperties.getDefaultBucketName();
            }
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucketName).object(uuidFileName).stream(
                                    is, is.available(), -1)
                            .build());
        }
    }

    /**
     * 返回临时带签名、过期时间一天、Get请求方式的访问URL
     *
     * @param bucketName  桶名
     * @param ossFilePath Oss文件路径
     * @return 返回临时带签名
     */
    @SneakyThrows
    public String getResignedObjectUrl(String bucketName, String ossFilePath) {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(ossFilePath)
                        .expiry(60 * 60 * 24)
                        .build());
    }

    /**
     * GetObject接口用于获取某个文件（Object）。此操作需要对此Object具有读权限。
     *
     * @param bucketName  桶名
     * @param ossFilePath Oss文件路径
     */
    @SneakyThrows
    public InputStream getObject(String bucketName, String ossFilePath) {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(ossFilePath).build());
    }

    /**
     * 查询桶的对象信息
     *
     * @param bucketName 桶名
     * @param recursive  是否递归查询
     * @return
     */
    @SneakyThrows
    public Iterable<Result<Item>> listObjects(String bucketName, boolean recursive) {
        return minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(recursive).build());
    }

    /**
     * 生成随机文件名，防止重复
     *
     * @param originalFilename 原始文件名
     * @return 文件名
     */
    public String generateOssUuidFileName(String originalFilename) {
        return "files" +
                SYMBOL_LEFT_SLASH +
                DateTime.now().toString("yyyy-MM-dd") +
                SYMBOL_LEFT_SLASH + UUID.randomUUID() +
                SYMBOL_LEFT_SLASH + originalFilename;
    }

    /**
     * 获取带签名的临时上传元数据对象，前端可获取后，直接上传到Minio
     *
     * @param bucketName
     * @param fileName
     * @return
     */
    @SneakyThrows
    public Map<String, String> getResignedPostFormData(String bucketName, String fileName) {
        // 为存储桶创建一个上传策略，过期时间为7天
        PostPolicy policy = new PostPolicy(bucketName, ZonedDateTime.now().plusDays(7));
        // 设置一个参数key，值为上传对象的名称
        policy.addEqualsCondition("key", fileName);
        // 添加Content-Type以"image/"开头，表示只能上传照片
        policy.addStartsWithCondition("Content-Type", "image/");
        // 设置上传文件的大小 64kiB to 10MiB.
        policy.addContentLengthRangeCondition(64 * 1024, 10 * 1024 * 1024);
        return minioClient.getPresignedPostFormData(policy);
    }

    /**
     * 初始化默认存储桶
     */
    @PostConstruct
    public void initDefaultBucket() {
        String defaultBucketName = minioProperties.getDefaultBucketName();
        if (bucketExists(defaultBucketName)) {
            log.info("默认存储桶已存在");
        } else {
            log.info("创建默认存储桶");
            makeBucket(minioProperties.getDefaultBucketName());
        }
    }
}
