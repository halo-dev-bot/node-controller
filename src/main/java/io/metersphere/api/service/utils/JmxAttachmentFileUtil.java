package io.metersphere.api.service.utils;

import io.metersphere.api.enums.StorageConstants;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.URLParserUtil;
import io.metersphere.api.repository.FileCenter;
import io.metersphere.dto.AttachmentBodyFile;
import io.metersphere.enums.JmxFileMetadataColumns;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.TemporaryFileUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.protocol.http.util.HTTPFileArg;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JmxAttachmentFileUtil {

    private TemporaryFileUtil temporaryFileUtil;

    /**
     * 解析JMX中的附件相关信息
     */
    public void parseJmxAttachmentFile(HashTree testPlan, String reportId, String platformUrl) {
        if (temporaryFileUtil == null) {
            temporaryFileUtil = CommonBeanFactory.getBean(TemporaryFileUtil.class);
        }
        /*
         1.解析jmx中需要的附件，并赋值为本地路径
         2.获取jmx中附件对应的node节点附件路径
            2.1 从本地获取
            2.2 本地未找到的，在minio下载，然后缓存到本地
            2.3 minio未找到的，在主工程下载。无法判断是否更新的文件不缓存
         */
        if (testPlan != null) {
            List<AttachmentBodyFile> bodyFileList = parseAttachmentBodyFile(testPlan);
            this.getAttachmentFile(bodyFileList, reportId, platformUrl);
        }
    }

    private List<AttachmentBodyFile> parseAttachmentBodyFile(HashTree jmxHashTree) {
        List<AttachmentBodyFile> bodyFileList = new ArrayList<>();
        initAttachmentBodyFile(jmxHashTree, bodyFileList);
        return bodyFileList;
    }

    /**
     * 获取附件对应的本地临时文件路径
     * 1 获取minio/git的文件。 （已下载过不会重复下载；未下载过的下载完之后会记录到执行文件夹内）
     * 2 对于local（主工程硬盘）上面的，连接主工程下载并记录到执行文件夹内
     */
    private void getAttachmentFile(List<AttachmentBodyFile> bodyFileList, String reportId, String platformUrl) {
        List<AttachmentBodyFile> downloadFromApiServer = new ArrayList<>();
        List<AttachmentBodyFile> downloadFromRepository = new ArrayList<>();

        //检查Local类型的文件在本地是否存在
        if (CollectionUtils.isNotEmpty(bodyFileList)) {
            bodyFileList.forEach(bodyFile -> {
                String filePath = this.deleteBodyPath(bodyFile.getFilePath());
                File file = temporaryFileUtil.getFile(null, 0, filePath);
                if (file == null) {
                    if (StringUtils.equalsAny(bodyFile.getFileStorage(), StorageConstants.MINIO.name(), StorageConstants.GIT.name())) {
                        downloadFromRepository.add(bodyFile);
                    } else {
                        downloadFromApiServer.add(bodyFile);
                    }
                }
            });
        }

        //获取minio、git文件
        FileCenter.getFilePath(downloadFromRepository);

        //  Metersphere下载
        if (CollectionUtils.isNotEmpty(downloadFromApiServer)) {
            try {
                String uri = URLParserUtil.getDownFileURL(platformUrl);
                List<BodyFile> files = new ArrayList<>();
                downloadFromApiServer.forEach(attachmentBodyFile -> {
                    BodyFile bodyFile = new BodyFile();
                    bodyFile.setRefResourceId(attachmentBodyFile.getFileMetadataId());
                    if (StringUtils.isNotEmpty(attachmentBodyFile.getFilePath())) {
                        bodyFile.setName(attachmentBodyFile.getFilePath());
                    } else {
                        bodyFile.setName(attachmentBodyFile.getName());
                    }
                    files.add(bodyFile);
                });
                BodyFileRequest request = new BodyFileRequest(reportId, files);
                String downloadPath = temporaryFileUtil.generateFileDir(null);
                this.mkDir(downloadPath);
                File bodyFile = ZipSpider.downloadFile(uri, request, temporaryFileUtil.generateFileDir(null));
                if (bodyFile != null) {
                    //解压文件直接到缓存目录中。
                    ZipSpider.unzip(bodyFile.getPath(), downloadPath);
                    FileUtils.deleteFile(bodyFile.getPath());
                }
            } catch (Exception e) {
                LoggerUtil.error("连接API-TEST下载附件失败!");
            }
        }
    }

    private void mkDir(String folderPath) {
        File file = new File(folderPath);
        if (!file.exists() || !file.isDirectory()) {
            file.mkdirs();
        }
    }

    private String deleteBodyPath(String filePath) {
        if (StringUtils.startsWith(filePath, FileUtils.BODY_FILE_DIR + "/")) {
            filePath = StringUtils.substring(filePath, FileUtils.BODY_FILE_DIR.length() + 1);
        }
        return filePath;
    }

    private void initAttachmentBodyFile(HashTree tree, List<AttachmentBodyFile> files) {
        for (Object key : tree.keySet()) {
            if (key == null) {
                continue;
            }
            HashTree node = tree.get(key);
            if (key instanceof HTTPSamplerProxy) {
                dealWithHttp(key, files);
            } else if (key instanceof CSVDataSet) {
                dealWithCsv(key, files);
            }
            if (node != null) {
                initAttachmentBodyFile(node, files);
            }
        }
    }

    private void dealWithHttp(Object key, List<AttachmentBodyFile> files) {
        if (key == null) {
            return;
        }
        HTTPSamplerProxy source = (HTTPSamplerProxy) key;
        for (HTTPFileArg httpFileArg : source.getHTTPFiles()) {
            AttachmentBodyFile file = getAttachmentBodyFileByTestElement(httpFileArg);
            if (file != null) {
                files.add(file);
            }
        }
    }

    private void dealWithCsv(Object key, List<AttachmentBodyFile> files) {
        CSVDataSet source = (CSVDataSet) key;
        if (StringUtils.isNotEmpty(source.getPropertyAsString(FileUtils.FILENAME))) {
            AttachmentBodyFile file = getAttachmentBodyFileByTestElement(source);
            if (file != null) {
                files.add(file);
            }
        }
    }

    private AttachmentBodyFile getAttachmentBodyFileByTestElement(TestElement testElement) {
        AttachmentBodyFile file = null;
        if (testElement != null) {

            file = new AttachmentBodyFile();
            file.setId(testElement.getPropertyAsString(FileUtils.FILENAME));
            file.setName(testElement.getPropertyAsString(FileUtils.FILENAME));
            if (testElement.getPropertyAsBoolean(FileUtils.IS_REF)) {
                file.setRef(true);
            }
            if (StringUtils.isNotBlank(testElement.getPropertyAsString(FileUtils.FILE_ID))) {
                file.setFileMetadataId(testElement.getPropertyAsString(FileUtils.FILE_ID));
            }
            if (StringUtils.isNotBlank(testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_NAME.name()))) {
                file.setName(testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_NAME.name()));
            }
            if (StringUtils.isNotBlank(testElement.getPropertyAsString(FileUtils.FILE_PATH))) {
                file.setFilePath(testElement.getPropertyAsString(FileUtils.FILE_PATH));
            }
            if (testElement.getPropertyAsLong(JmxFileMetadataColumns.REF_FILE_UPDATE_TIME.name()) > 0) {
                file.setFileUpdateTime(testElement.getPropertyAsLong(JmxFileMetadataColumns.REF_FILE_UPDATE_TIME.name()));
            }
            if (StringUtils.isNotBlank(testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_STORAGE.name()))) {
                file.setFileStorage(testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_STORAGE.name()));
            }

            if (StringUtils.isNotBlank(testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_PROJECT_ID.name()))) {
                file.setProjectId(testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_PROJECT_ID.name()));
            }

            if (StringUtils.isNotBlank(testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_ATTACH_INFO.name()))) {
                String fileAttachInfo = testElement.getPropertyAsString(JmxFileMetadataColumns.REF_FILE_ATTACH_INFO.name());
                file.setFileAttachInfoJson(fileAttachInfo);
            }

            String localPath = null;
            if (StringUtils.equalsAny(file.getFileStorage(), StorageConstants.GIT.name(), StorageConstants.MINIO.name())) {
                localPath = temporaryFileUtil.generateFilePath(
                        file.getProjectId(),
                        file.getFileUpdateTime(),
                        file.getName()
                );
            } else {
                localPath = temporaryFileUtil.generateFilePath(null, 0, this.deleteBodyPath(file.getFilePath()));
            }

            testElement.setProperty(FileUtils.FILE_PATH, localPath);
            if (testElement instanceof HTTPFileArg) {
                ((HTTPFileArg) testElement).setPath(localPath);
            }
        }
        return file;
    }
}
