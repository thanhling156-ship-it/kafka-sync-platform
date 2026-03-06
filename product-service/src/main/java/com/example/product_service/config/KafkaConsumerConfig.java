package com.example.product_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        // Sử dụng hàm chuẩn không tham số của Spring Boot 4.0
        Map<String, Object> configProps = kafkaProperties.buildConsumerProperties();

        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Ở tầng thấp ta dùng String/Byte, Converter sẽ lo phần Object JSON
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Đảm bảo trỏ đúng về kafka trong Docker
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Dùng JacksonJsonMessageConverter chuẩn 2026
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

        // Cấu hình TypeMapper để nhận diện UserRegistrationEvent từ module common
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(DefaultJacksonJavaTypeMapper.TypePrecedence.TYPE_ID);
        // CỰC KỲ QUAN TRỌNG: Tin tưởng gói common để không bị lỗi bảo mật khi nhận tin
        typeMapper.addTrustedPackages("com.example.common.dto");

        converter.setTypeMapper(typeMapper);
        factory.setRecordMessageConverter(converter);

        return factory;
    }
}