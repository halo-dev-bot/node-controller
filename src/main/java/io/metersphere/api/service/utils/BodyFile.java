package io.metersphere.api.service.utils;

import lombok.Data;

@Data
public class BodyFile {
    private String id;
    private String name;
    private String storage;
    // 调试附件处理
    private String refResourceId;
}
