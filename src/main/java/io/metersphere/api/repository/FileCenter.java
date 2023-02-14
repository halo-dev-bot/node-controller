package io.metersphere.api.repository;

import io.metersphere.api.enums.StorageConstants;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.dto.AttachmentBodyFile;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FileCenter {
    public static FileRepository getRepository(String storage) {
        if (StringUtils.equals(StorageConstants.MINIO.name(), storage)) {
            LoggerUtil.info("NAS文件处理");
            return CommonBeanFactory.getBean(MinIORepositoryImpl.class);
        } else {
            return null;
        }
    }

    public static List<AttachmentBodyFile> downloadFiles(List<AttachmentBodyFile> downLoadFileList) {

        List<AttachmentBodyFile> downloadErrorList = new ArrayList<>();

        if (CollectionUtils.isNotEmpty(downLoadFileList)) {
            List<AttachmentBodyFile> minIODownloadFiles = new ArrayList<>();
            downLoadFileList.forEach(attachmentBodyFile -> {
                if (StringUtils.equals(StorageConstants.MINIO.name(), attachmentBodyFile.getFileStorage())) {
                    minIODownloadFiles.add(attachmentBodyFile);
                }
            });
            downloadErrorList = Objects.requireNonNull(getRepository(StorageConstants.MINIO.name())).downloadFiles(minIODownloadFiles);
        }
        return downloadErrorList;
    }
}
