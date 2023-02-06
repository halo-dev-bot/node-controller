package io.metersphere.api.service;

import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.enums.MinIOConfigEnum;
import io.metersphere.utils.LoggerUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MinIOConfigService {

    public static final String bucket = "metersphere";
    private Map<String, MinioClient> minIoTemplateMap = new ConcurrentHashMap<>();

    public MinioClient init(Map<String, Object> minioConfig) {
        try {
            Object serverUrl = minioConfig.get(MinIOConfigEnum.ENDPOINT).toString();
            if (serverUrl != null && minIoTemplateMap.containsKey(serverUrl.toString())) {
                return minIoTemplateMap.get(serverUrl);
            } else {
                // 创建 MinioClient 客户端
                MinioClient minioClient = MinioClient.builder()
                        .endpoint(minioConfig.get(MinIOConfigEnum.ENDPOINT).toString())
                        .credentials(minioConfig.get(MinIOConfigEnum.ACCESS_KEY).toString(), minioConfig.get(MinIOConfigEnum.SECRET_KEY).toString())
                        .build();
                boolean exist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exist) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                minIoTemplateMap.put(minioConfig.get(MinIOConfigEnum.ENDPOINT).toString(), minioClient);
                return minioClient;
            }
        } catch (Exception e) {
            LoggerUtil.error(e);
            return null;
        }
    }

    public byte[] getFile(Map<String, Object> minioConfig, String jarName) throws Exception {
        return getFileAsStream(minioConfig, jarName).readAllBytes();
    }

    public InputStream getFileAsStream(Map<String, Object> minioConfig, String jarName) throws Exception {
        MinioClient minioClient = this.init(minioConfig);
        if (minioClient != null) {
            String path = StringUtils.join(FileUtils.BODY_FILE_DIR, "/plugin", jarName, File.separator, jarName);
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(path)
                    .build());
        }
        return null;
    }

}