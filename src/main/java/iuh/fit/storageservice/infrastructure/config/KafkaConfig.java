package iuh.fit.storageservice.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.topic.file-move}")
    private String topic;

    @Bean
    public NewTopic fileMoveTopic() {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(2)
                .build();
    }
}
