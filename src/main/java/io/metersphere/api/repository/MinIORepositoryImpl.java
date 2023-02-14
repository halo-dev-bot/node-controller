package io.metersphere.api.repository;

import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.dto.AttachmentBodyFile;
import io.metersphere.enums.MinIOConfigEnum;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.TemporaryFileUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
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
        File file = temporaryFileUtil.getFile(request.getProjectId(), request.getFileUpdateTime(), request.getName());
        if (file == null) {
            byte[] fileBytes = getFileAsStream(request.getRemotePath()).readAllBytes();
            //附件存储到缓存目录中
            temporaryFileUtil.saveFile(request.getProjectId(), request.getFileUpdateTime(), request.getName(), fileBytes);
            return temporaryFileUtil.getFile(request.getProjectId(), request.getFileUpdateTime(), request.getName());
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
        try {
            Object serverUrl = minioConfig.get(MinIOConfigEnum.ENDPOINT).toString();
            if (minioClient == null && serverUrl != null) {
                // 创建 MinioClient 客户端
                minioClient = MinioClient.builder()
                        .endpoint(minioConfig.get(MinIOConfigEnum.ENDPOINT).toString())
                        .credentials(minioConfig.get(MinIOConfigEnum.ACCESS_KEY).toString(), minioConfig.get(MinIOConfigEnum.SECRET_KEY).toString())
                        .build();
                boolean exist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exist) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(e);
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