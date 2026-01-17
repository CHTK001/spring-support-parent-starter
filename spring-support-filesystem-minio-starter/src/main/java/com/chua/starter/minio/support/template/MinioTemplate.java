package com.chua.starter.minio.support.template;

import com.chua.common.support.core.utils.StringUtils;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.minio.support.properties.MinioProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * minio
 *
 * @author CH
 */
public class MinioTemplate implements InitializingBean {

    private final MinioProperties minioProperties;
    private final ModuleLog log = ModuleLog.of("Minio", MinioTemplate.class);
    private MinioClient minioClient;

    public MinioTemplate(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        var client = MinioClient.builder()
                .endpoint(minioProperties.getAddress())
                .credentials(minioProperties.getUsername(), minioProperties.getPassword())
                .build();
        this.minioClient = new PearlMinioClient(client);
    }

    /**
     * 查询所有存储桶
     *
     * @return Bucket 集合
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public List<Bucket> listBuckets() throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        return minioClient.listBuckets();
    }

    /**
     * 桶是否存在
     *
     * @param bucketName 桶名
     * @return 是否存在
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public boolean bucketExists(String bucketName) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 桶名
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public void makeBucket(String bucketName) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        if (!bucketExists(bucketName)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 删除一个空桶 如果存储桶存在对象不为空时，删除会报错。
     *
     * @param bucketName 桶名
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public void removeBucket(String bucketName) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }

    /**
     * 上传文件
     *
     * @param inputStream      流
     * @param originalFileName 原始文件名
     * @param bucketName       桶名
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     * @throws java.io.IOException    IO 异常
     */
    @SneakyThrows
    public void putObject(InputStream inputStream, String bucketName, String originalFileName) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
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
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public String getResignedObjectUrl(String bucketName, String ossFilePath) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
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
     * @return 文件输入流
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public InputStream getObject(String bucketName, String ossFilePath) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(ossFilePath).build());
    }

    /**
     * 查询桶的对象信息
     *
     * @param bucketName 桶名
     * @param recursive  是否递归查询
     * @return 对象结果迭代器
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public Iterable<Result<Item>> listObjects(String bucketName, boolean recursive) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
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
                "/" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                "/" + UUID.randomUUID() +
                "/" + originalFilename;
    }

    /**
     * 获取带签名的临时上传元数据对象，前端可获取后，直接上传到Minio
     *
     * @param bucketName 桶名
     * @param fileName   文件名
     * @return 带签名的表单数据
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @SneakyThrows
    public Map<String, String> getResignedPostFormData(String bucketName, String fileName) throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
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
     *
     * @throws ErrorResponseException   MinIO 错误响应异常
     * @throws InsufficientDataException 数据不足异常
     * @throws InternalException 内部异常
     * @throws InvalidKeyException 无效密钥异常
     * @throws InvalidResponseException 无效响应异常
     * @throws IOException IO 异常
     * @throws NoSuchAlgorithmException 无此算法异常
     * @throws ServerException 服务器异常
     * @throws XmlParserException XML 解析异常
     */
    @PostConstruct
    @SneakyThrows
    public void initDefaultBucket() throws ErrorResponseException, InsufficientDataException, InternalException, InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException, ServerException, XmlParserException {
        String defaultBucketName = minioProperties.getDefaultBucketName();
        if (bucketExists(defaultBucketName)) {
            log.info("默认存储桶 {} 已存在", highlight(defaultBucketName));
        } else {
            log.info("创建默认存储桶: {}", highlight(defaultBucketName));
            makeBucket(minioProperties.getDefaultBucketName());
        }
        log.info("服务已连接 {}", address(minioProperties.getAddress()));
    }
}
