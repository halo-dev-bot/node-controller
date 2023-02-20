package io.metersphere.api.repository;

import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.dto.AttachmentBodyFile;
import io.metersphere.enums.MinIOConfigEnum;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.TemporaryFileUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MinIORepositoryImpl implements FileRepository {

    public static final String bucket = "metersphere";
    private static MinioClient minioClient = null;

    @Resource
    private TemporaryFileUtil temporaryFileUtil;

    public File getFile(AttachmentBodyFile request) throws Exception {
        if (temporaryFileUtil == null) {
            temporaryFileUtil = CommonBeanFactory.getBean(TemporaryFileUtil.class);
        }
        assert temporaryFileUtil != null;
        File file = temporaryFileUtil.getFile(request.getProjectId(), request.getFileMetadataId(), request.getFileUpdateTime(), request.getName(), request.getFileType());
        if (file == null) {
            byte[] fileBytes = getFileAsStream(request.getRemotePath()).readAllBytes();
            //附件存储到缓存目录中
            temporaryFileUtil.saveFile(request.getProjectId(), request.getFileMetadataId(), request.getFileUpdateTime(), request.getName(), request.getFileType(), fileBytes);
            return temporaryFileUtil.getFile(request.getProjectId(), request.getFileMetadataId(), request.getFileUpdateTime(), request.getName(), request.getFileType());
        } else {
            return file;
        }
    }

    @Override
    public List<AttachmentBodyFile> downloadFiles(List<AttachmentBodyFile> attachmentBodyFileList) {
        if (temporaryFileUtil == null) {
            temporaryFileUtil = CommonBeanFactory.getBean(TemporaryFileUtil.class);
        }
        List<AttachmentBodyFile> errorList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(attachmentBodyFileList)) {
            attachmentBodyFileList.forEach(attachmentBodyFile -> {
                try {
                    attachmentBodyFile.setRemotePath(attachmentBodyFile.getProjectId() + "/" + attachmentBodyFile.getName());
                    File file = this.getFile(attachmentBodyFile);
                    if (file == null) {
                        errorList.add(attachmentBodyFile);
                    }
                } catch (Exception e) {
                    LoggerUtil.error(e);
                }
            });
        }
        return errorList;
    }

    public void initMinioClient(Map<String, Object> minioConfig) {
        if (MapUtils.isEmpty(minioConfig)) {
            LoggerUtil.info("初始化MinIO插件停止：参数[minioConfig]为空。");
            return;
        }
        try {
            Object serverUrl = minioConfig.get(MinIOConfigEnum.ENDPOINT).toString();
            if (minioClient == null && serverUrl != null) {
                LoggerUtil.info("开始初始化MinIO插件。配置：", JsonUtils.toJSONString(minioConfig));
                // 创建 MinioClient 客户端
                minioClient = MinioClient.builder()
                        .endpoint(minioConfig.get(MinIOConfigEnum.ENDPOINT).toString())
                        .credentials(minioConfig.get(MinIOConfigEnum.ACCESS_KEY).toString(), minioConfig.get(MinIOConfigEnum.SECRET_KEY).toString())
                        .build();
                boolean exist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exist) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                LoggerUtil.info("初始化MinIO插件结束。");
            } else {
                LoggerUtil.info("MinIOClient已初始化，无需再配置。");
            }
        } catch (Exception e) {
            LoggerUtil.error("MinIOClient已初始化失败！", e);
        }
    }

    public InputStream getFileAsStream(String path) throws Exception {
        if (minioClient != null) {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(path)
                    .build());
        }
        return null;
    }

}