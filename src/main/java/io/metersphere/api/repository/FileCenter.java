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
        } else if (StringUtils.equals(StorageConstants.GIT.name(), storage)) {
            LoggerUtil.info("Git文件处理");
            return new GitRepositoryImpl();
        } else {
            return null;
        }
    }

    /**
     * 批量下载文件. 下载成功的文件会存储在对应model的fileBytes字段中。
     */
    public static List<AttachmentBodyFile> batchDownLoadFileInList(List<AttachmentBodyFile> downLoadFileList) {
        List<AttachmentBodyFile> returnList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(downLoadFileList)) {
            List<AttachmentBodyFile> minIODownloadFiles = new ArrayList<>();
            List<AttachmentBodyFile> gitDownloadFiles = new ArrayList<>();
            downLoadFileList.forEach(attachmentBodyFile -> {
                if (StringUtils.equals(StorageConstants.MINIO.name(), attachmentBodyFile.getFileStorage())) {
                    minIODownloadFiles.add(attachmentBodyFile);
                } else if (StringUtils.equals(StorageConstants.GIT.name(), attachmentBodyFile.getFileStorage())) {
                    gitDownloadFiles.add(attachmentBodyFile);
                } else {
                    returnList.add(attachmentBodyFile);
                }
            });
            returnList.addAll(Objects.requireNonNull(getRepository(StorageConstants.MINIO.name())).getFileBatch(minIODownloadFiles));
            returnList.addAll(Objects.requireNonNull(getRepository(StorageConstants.GIT.name())).getFileBatch(gitDownloadFiles));
        }
        return returnList;
    }
}
