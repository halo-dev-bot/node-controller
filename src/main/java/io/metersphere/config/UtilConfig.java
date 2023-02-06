package io.metersphere.config;

import io.metersphere.api.service.utils.JmxAttachmentFileUtil;
import io.metersphere.utils.TemporaryFileUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilConfig {
    @Bean
    public TemporaryFileUtil temporaryFileUtil() {
        return new TemporaryFileUtil(TemporaryFileUtil.NODE_FILE_FOLDER);
    }

    @Bean
    public JmxAttachmentFileUtil jmxAttachmentFileUtil() {
        return new JmxAttachmentFileUtil();
    }
}
