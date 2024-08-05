package com.library.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@org.springframework.context.annotation.Configuration
public class Configuration {

    private final String topic;
    private final int partitions;
    private final int replicas;

    public Configuration(
            @Value("${spring.kafka.topic.name}") String topic,
            @Value("${spring.kafka.topic.partitions:3}") int partitions,
            @Value("${spring.kafka.topic.replicas:3}") int replicas
    ) {
        this.topic = topic;
        this.partitions = partitions;
        this.replicas = replicas;
    }

    @Bean
    public NewTopic libraryTopic(){
        return TopicBuilder
                .name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

}
