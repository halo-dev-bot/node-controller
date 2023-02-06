package io.metersphere.api.repository;


import io.metersphere.dto.AttachmentBodyFile;

import java.util.List;

public interface FileRepository {
    List<AttachmentBodyFile> getFileBatch(List<AttachmentBodyFile> requestList);

    List<AttachmentBodyFile> getFilePath(List<AttachmentBodyFile> requestList);
}
