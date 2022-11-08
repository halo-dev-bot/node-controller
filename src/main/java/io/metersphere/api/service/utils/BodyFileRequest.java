package io.metersphere.api.service.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BodyFileRequest {
    private String reportId;
    private List<BodyFile> bodyFiles;
}
