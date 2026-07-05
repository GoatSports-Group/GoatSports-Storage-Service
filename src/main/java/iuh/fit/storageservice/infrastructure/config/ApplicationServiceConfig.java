package iuh.fit.storageservice.infrastructure.config;

import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.application.service.impl.StorageServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationServiceConfig {

    @Bean
    public StorageServiceImpl storageService(StoragePort storagePort) {
        return new StorageServiceImpl(storagePort);
    }
}