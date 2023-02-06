package io.metersphere.api.repository;

import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.util.GitRepositoryUtil;
import io.metersphere.api.vo.RemoteFileAttachInfo;
import io.metersphere.api.vo.RepositoryRequest;
import io.metersphere.dto.AttachmentBodyFile;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.TemporaryFileUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitRepositoryImpl implements FileRepository {
    private TemporaryFileUtil temporaryFileUtil;

    @Override
    public List<AttachmentBodyFile> getFileBatch(List<AttachmentBodyFile> allRequests) {
        if (temporaryFileUtil == null) {
            temporaryFileUtil = CommonBeanFactory.getBean(TemporaryFileUtil.class);
        }
        List<AttachmentBodyFile> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(allRequests)) {
            Map<String, List<AttachmentBodyFile>> requestGroupByRepository = this.getRepositoryMap(allRequests);

            for (Map.Entry<String, List<AttachmentBodyFile>> entry : requestGroupByRepository.entrySet()) {
                List<AttachmentBodyFile> requestList = entry.getValue();
                RemoteFileAttachInfo baseGitFileInfo = null;

                List<RepositoryRequest> downloadFileList = new ArrayList<>();
                Map<String, byte[]> fileByteMap = new HashMap<>();
                for (AttachmentBodyFile fileRequest : requestList) {
                    RemoteFileAttachInfo gitFileInfo = JsonUtils.parseObject(fileRequest.getFileAttachInfoJson(), RemoteFileAttachInfo.class);
                    if (gitFileInfo == null) {
                        continue;
                    }
                    if (baseGitFileInfo == null) {
                        baseGitFileInfo = gitFileInfo;
                    }
                    //先在临时目录中查找
                    File file = temporaryFileUtil.getFile(fileRequest.getProjectId(), fileRequest.getFileUpdateTime(), fileRequest.getName());
                    if (file == null) {
                        downloadFileList.add(new RepositoryRequest() {{
                            this.setCommitId(gitFileInfo.getCommitId());
                            this.setFilePath(gitFileInfo.getFilePath());
                            this.setFileMetadataId(fileRequest.getFileMetadataId());
                            this.setProjectId(fileRequest.getProjectId());
                            this.setFileName(fileRequest.getName());
                            this.setUpdateTime(fileRequest.getFileUpdateTime());
                        }});
                    } else {
                        fileByteMap.put(fileRequest.getFileMetadataId(), temporaryFileUtil.fileToByte(file));
                    }
                }

                if (CollectionUtils.isNotEmpty(downloadFileList) && baseGitFileInfo != null) {
                    GitRepositoryUtil repositoryUtils = new GitRepositoryUtil(
                            baseGitFileInfo.getRepositoryPath(),
                            baseGitFileInfo.getUserName(), baseGitFileInfo.getToken());
                    try {
                        Map<String, byte[]> downloadFileMap = repositoryUtils.getFiles(downloadFileList);
                        downloadFileList.forEach(repositoryFile -> {
                            if (downloadFileMap.get(repositoryFile.getFileMetadataId()) != null) {
                                //附件存储到缓存目录中
                                temporaryFileUtil.saveFileByParamCheck(
                                        repositoryFile.getProjectId(),
                                        repositoryFile.getUpdateTime(),
                                        repositoryFile.getFileName(),
                                        downloadFileMap.get(repositoryFile.getFileMetadataId()));
                            }
                        });
                        fileByteMap.putAll(downloadFileMap);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                requestList.forEach(fileRequest -> {
                    if (fileByteMap.get(fileRequest.getFileMetadataId()) != null) {
                        fileRequest.setFileBytes(fileByteMap.get(fileRequest.getFileMetadataId()));
                    }
                });
                list.addAll(requestList);
            }
        }
        return list;
    }

    @Override
    public List<AttachmentBodyFile> getFilePath(List<AttachmentBodyFile> allRequests) {
        if (temporaryFileUtil == null) {
            temporaryFileUtil = CommonBeanFactory.getBean(TemporaryFileUtil.class);
        }
        List<AttachmentBodyFile> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(allRequests)) {
            Map<String, List<AttachmentBodyFile>> requestGroupByRepository = this.getRepositoryMap(allRequests);
            for (Map.Entry<String, List<AttachmentBodyFile>> entry : requestGroupByRepository.entrySet()) {
                List<AttachmentBodyFile> requestList = entry.getValue();
                RemoteFileAttachInfo baseGitFileInfo = null;
                List<RepositoryRequest> downloadFileList = new ArrayList<>();
                Map<String, File> fileMap = new HashMap<>();

                for (AttachmentBodyFile fileRequest : requestList) {
                    RemoteFileAttachInfo gitFileInfo = JsonUtils.parseObject(fileRequest.getFileAttachInfoJson(), RemoteFileAttachInfo.class);
                    if (gitFileInfo == null) {
                        continue;
                    }
                    if (baseGitFileInfo == null) {
                        baseGitFileInfo = gitFileInfo;
                    }
                    //先在临时目录中查找
                    File file = temporaryFileUtil.getFile(fileRequest.getProjectId(), fileRequest.getFileUpdateTime(), fileRequest.getName());
                    if (file != null && file.exists()) {
                        fileMap.put(fileRequest.getFileMetadataId(), file);
                    } else {
                        downloadFileList.add(new RepositoryRequest() {{
                            this.setCommitId(gitFileInfo.getCommitId());
                            this.setFilePath(gitFileInfo.getFilePath());
                            this.setFileMetadataId(fileRequest.getFileMetadataId());
                            this.setProjectId(fileRequest.getProjectId());
                            this.setFileName(fileRequest.getName());
                            this.setUpdateTime(fileRequest.getFileUpdateTime());
                        }});
                    }
                }

                if (CollectionUtils.isNotEmpty(downloadFileList) && baseGitFileInfo != null) {
                    GitRepositoryUtil repositoryUtils = new GitRepositoryUtil(
                            baseGitFileInfo.getRepositoryPath(),
                            baseGitFileInfo.getUserName(), baseGitFileInfo.getToken());
                    try {
                        Map<String, byte[]> downloadFileMap = repositoryUtils.getFiles(downloadFileList);
                        downloadFileList.forEach(repositoryFile -> {
                            if (downloadFileMap.get(repositoryFile.getFileMetadataId()) != null) {
                                //附件存储到缓存目录中
                                temporaryFileUtil.saveFileByParamCheck(

                                        repositoryFile.getProjectId(),
                                        repositoryFile.getUpdateTime(),
                                        repositoryFile.getFileName(),
                                        downloadFileMap.get(repositoryFile.getFileMetadataId()));
                                fileMap.put(repositoryFile.getFileMetadataId(),
                                        temporaryFileUtil.getFile(
                                                repositoryFile.getProjectId(),
                                                repositoryFile.getUpdateTime(),
                                                repositoryFile.getFileName()));
                            }
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                list.addAll(requestList);
            }
        }
        return list;
    }

    private Map<String, List<AttachmentBodyFile>> getRepositoryMap(List<AttachmentBodyFile> allRequests) {
        Map<String, List<AttachmentBodyFile>> requestGroupByRepository = new HashMap<>();
        for (AttachmentBodyFile request : allRequests) {
            if (StringUtils.isNotBlank(request.getFileAttachInfoJson())) {
                try {
                    RemoteFileAttachInfo gitFileInfo = JsonUtils.parseObject(request.getFileAttachInfoJson(), RemoteFileAttachInfo.class);
                    String repositoryInfo = gitFileInfo.getRepositoryPath() + "-" + gitFileInfo.getUserName() + "-" + gitFileInfo.getToken();
                    if (requestGroupByRepository.containsKey(repositoryInfo)) {
                        requestGroupByRepository.get(repositoryInfo).add(request);
                    } else {
                        requestGroupByRepository.put(repositoryInfo, new ArrayList<>() {{
                            this.add(request);
                        }});
                    }
                } catch (Exception e) {
                    LoggerUtil.error("解析Git仓库信息出错!" + request.getFileAttachInfoJson(), e);
                }
            }
        }
        return requestGroupByRepository;
    }
}
