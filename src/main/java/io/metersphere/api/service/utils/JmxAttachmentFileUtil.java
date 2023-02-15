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
import org.apache.jmeter.config.KeystoreConfig;
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
                File executeFile;
                if (StringUtils.isNotEmpty(bodyFile.getFileStorage()) && !StringUtils.equals(bodyFile.getFileStorage(), StorageConstants.LOCAL.name())) {
                    executeFile = temporaryFileUtil.getFile(bodyFile.getProjectId(), bodyFile.getFileMetadataId(), bodyFile.getFileUpdateTime(), bodyFile.getName());
                    if (executeFile == null) {
                        LoggerUtil.info("本次执行[" + reportId + "]需要下载的[" + bodyFile.getFileStorage() + "]文件【" + bodyFile.getFileUpdateTime() + "_" + bodyFile.getName() + "】在当前机器节点未找到！");
                        //区分MinIO下载还是api-server下载
                        if (StringUtils.equals(bodyFile.getFileStorage(), StorageConstants.MINIO.name())) {
                            downloadFromRepository.add(bodyFile);
                        } else {
                            downloadFromApiServer.add(bodyFile);
                        }
                        //存在未找到的文件，还有一种可能是执行文件夹中存在过期文件。这时候执行一次删除判断。
                        temporaryFileUtil.deleteOldFile(bodyFile.getProjectId(), bodyFile.getFileMetadataId(), bodyFile.getFileUpdateTime(), bodyFile.getName());
                    } else {
                        LoggerUtil.info("本次执行[" + reportId + "]需要下载的[" + bodyFile.getFileStorage() + "]文件【" + bodyFile.getName() + "】在当前机器节点已找到，无需下载。");
                    }
                } else {
                    String filePath = this.substringBodyPath(bodyFile.getFilePath());
                    executeFile = temporaryFileUtil.getFile(null, null, 0, filePath);
                    if (executeFile == null) {
                        LoggerUtil.info("本次执行[" + reportId + "]需要下载的[Local]文件【" + filePath + "】在当前机器节点未找到！");
                        downloadFromApiServer.add(bodyFile);
                    } else {
                        LoggerUtil.info("本次执行[" + reportId + "]需要下载的[Local]文件【" + filePath + "】在当前机器节点已找到，无需下载。");
                    }
                }
            });
        }

        //获取MinIO文件
        List<AttachmentBodyFile> downloadErrorList = FileCenter.downloadFiles(downloadFromRepository);
        LoggerUtil.info("本次执行[" + reportId + "]在文件库中需要下载[" + downloadFromRepository.size() + "]个文件，已下载完毕。");
        if (CollectionUtils.isNotEmpty(downloadErrorList)) {
            downloadFromApiServer.addAll(downloadErrorList);
        }
        //  API-TEST下载
        if (CollectionUtils.isNotEmpty(downloadFromApiServer)) {
            try {
                String uri = URLParserUtil.getDownFileURL(platformUrl);
                List<BodyFile> files = new ArrayList<>();
                downloadFromApiServer.forEach(attachmentBodyFile -> {
                    BodyFile bodyFile = new BodyFile();
                    bodyFile.setRefResourceId(attachmentBodyFile.getFileMetadataId());
                    bodyFile.setStorage(attachmentBodyFile.getFileStorage());
                    if (StringUtils.isNotEmpty(attachmentBodyFile.getFilePath())) {
                        bodyFile.setName(attachmentBodyFile.getFilePath());
                    } else {
                        bodyFile.setName(attachmentBodyFile.getName());
                    }
                    files.add(bodyFile);
                });
                BodyFileRequest request = new BodyFileRequest(reportId, files);
                String downloadPath = temporaryFileUtil.fileFolder;
                this.mkDir(downloadPath);
                File bodyFile = ZipSpider.downloadFile(uri, request, downloadPath);
                if (bodyFile != null) {
                    //解压文件直接到缓存目录中。
                    ZipSpider.unzip(bodyFile.getPath(), downloadPath);
                    FileUtils.deleteFile(bodyFile.getPath());
                }
                LoggerUtil.info("本次执行[" + reportId + "]连接主工程Metersphere需要下载[" + downloadFromApiServer.size() + "]个文件，已下载完毕。");
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

    private String substringBodyPath(String filePath) {
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
            } else if (key instanceof KeystoreConfig) {
                dealWithKeystoreConfig(key, files);
            }

            if (node != null) {
                initAttachmentBodyFile(node, files);
            }
        }
    }

    public void dealWithKeystoreConfig(Object tree, List<AttachmentBodyFile> files) {

        if (tree != null) {
            KeystoreConfig source = (KeystoreConfig) tree;
            AttachmentBodyFile file = new AttachmentBodyFile();
            if (StringUtils.isNotBlank(source.getPropertyAsString(FileUtils.KEYSTORE_FILE_PATH))) {
                String filePath = source.getPropertyAsString(FileUtils.KEYSTORE_FILE_PATH);
                file.setFilePath(filePath);
            }

            String localPath = temporaryFileUtil.generateFilePath(null, null, 0, this.substringBodyPath(file.getFilePath()));
            //判断文本地件是否存在。如果存在则返回null。 文件库文件的本地校验在下载之前判断
            if (this.isFileExists(null, null, 0, this.substringBodyPath(file.getFilePath()))) {
                file = null;
            }
            source.setProperty(FileUtils.KEYSTORE_FILE_PATH, localPath);
            if (file != null) {
                files.add(file);
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

            String localPath;
            if (StringUtils.isNotEmpty(file.getFileStorage()) && !StringUtils.equals(file.getFileStorage(), StorageConstants.LOCAL.name())) {
                localPath = temporaryFileUtil.generateFilePath(
                        file.getProjectId(),
                        file.getFileMetadataId(),
                        file.getFileUpdateTime(),
                        file.getName()
                );
            } else {
                String filePath = StringUtils.isBlank(file.getFilePath()) ? file.getName() : file.getFilePath();
                localPath = temporaryFileUtil.generateFilePath(null, null, 0, this.substringBodyPath(filePath));
                //判断文本地件是否存在。如果存在则返回null。 文件库文件的本地校验在下载之前判断
                if (this.isFileExists(null, null, 0, this.substringBodyPath(file.getFilePath()))) {
                    file = null;
                }
            }

            if (StringUtils.isNotBlank(testElement.getPropertyAsString(FileUtils.FILE_PATH))) {
                testElement.setProperty(FileUtils.FILE_PATH, localPath);
            } else {
                testElement.setProperty(FileUtils.FILENAME, localPath);
            }

            if (testElement instanceof HTTPFileArg) {
                ((HTTPFileArg) testElement).setPath(localPath);
            }
        }
        return file;
    }

    private boolean isFileExists(String folder, String fileMetadataId, long updateTime, String fileName) {
        File localFile = temporaryFileUtil.getFile(folder, fileMetadataId, updateTime, fileName);
        return localFile != null;
    }

    public void deleteTmpFiles(String reportId) {
        if (StringUtils.isNotEmpty(reportId)) {
            String executeTmpFolder = StringUtils.join(
                    temporaryFileUtil.generateFileDir(null, null, 0),
                    File.separator,
                    "tmp",
                    File.separator,
                    reportId
            );
            try {
                FileUtils.deleteDir(executeTmpFolder);
            } catch (Exception e) {
                LoggerUtil.error("删除[" + reportId + "]执行中产生的临时文件失败!", e);
            }

        }
    }
}
