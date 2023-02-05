package com.smilebat.learntribe.reactor.kafka;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Producer for the sending notifications.
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Component
@Slf4j
@ToString
@RefreshScope
public class KafkaProducer {
  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @Value("${kafka.topic.out}")
  private String outTopic = "challenge-store-event-1";

  @Value("${kafka.startup}")
  private boolean startup;

  /**
   * Sends a notifcation to open ai processor service.
   *
   * @param message the message to be sent
   */
  public void sendMessage(String message) {
    try {
      if (startup) {

      kafkaTemplate.send(outTopic, message);
      }
    } catch (Exception e) {
      log.info("Unable to send message {} to {}", message, outTopic);
      throw new RuntimeException(e);
    }
  }
}
