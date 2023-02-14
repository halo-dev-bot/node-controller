package io.metersphere.api.repository;


import io.metersphere.dto.AttachmentBodyFile;

import java.util.List;

public interface FileRepository {
    List<AttachmentBodyFile> downloadFiles(List<AttachmentBodyFile> requestList);
}
