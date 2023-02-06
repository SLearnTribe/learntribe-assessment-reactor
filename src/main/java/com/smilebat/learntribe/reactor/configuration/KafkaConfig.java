package com.smilebat.learntribe.reactor.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Configuration required by Kafka for messaging.
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Configuration
@EnableKafka
@SuppressFBWarnings(justification = "Generated code")
public class KafkaConfig {

  @Value("${kafka.server.url}")
  private String kafkaLocalServerConfig;

  @Value("${kafka.groupid}")
  private String groupIdConfig;

  /**
   * Bean for producer factory.
   *
   * @return the {@link ProducerFactory}.
   */
  @Bean
  public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaLocalServerConfig);
    configMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    configMap.put(JsonDeserializer.TRUSTED_PACKAGES, "com.smilebat.learntribe");
    return new DefaultKafkaProducerFactory<String, Object>(configMap);
  }

  /**
   * Bean for Kafka Template.
   *
   * @return the {@link KafkaTemplate}.
   */
  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  /**
   * Bean for Consumer Factory
   *
   * @return the {@link ConsumerFactory}
   */
  @Bean
  public ConsumerFactory<String, String> consumerFactory() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaLocalServerConfig);
    configMap.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    configMap.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    configMap.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdConfig);
    configMap.put(JsonDeserializer.TRUSTED_PACKAGES, "com.smilebat.learntribe");
    return new DefaultKafkaConsumerFactory<>(configMap);
  }

  /**
   * Bean for Kafka Listner config.
   *
   * @return the {@link ConcurrentKafkaListenerContainerFactory}.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory());
    return factory;
  }
}
