package io.metersphere.api.vo;

import lombok.Data;

@Data
public class RepositoryRequest {
    private String fileMetadataId;
    private String filePath;
    private String commitId;
    private String projectId;
    private long updateTime;
    private String fileName;
}
