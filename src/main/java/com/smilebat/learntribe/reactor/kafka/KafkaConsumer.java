package com.smilebat.learntribe.reactor.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smilebat.learntribe.inquisitve.UserProfileRequest;
import com.smilebat.learntribe.kafka.KafkaSkillsRequest;
import com.smilebat.learntribe.reactor.services.AssessmentService;
import com.smilebat.learntribe.reactor.services.CoreAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for the receiving notifications.
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RefreshScope
public class KafkaConsumer {

  public static final String KAFKA_LISTENER_CONTAINER_FACTORY = "kafkaListenerContainerFactory";

  private final ObjectMapper mapper;
  private final CoreAssessmentService coreService;

  private final AssessmentService service;

  @Value("${kafka.groupid}")
  private final String groupId = "sb-group-1";

  @Value("${kafka.topic.in.inq}")
  private final String inTopicInq = "assessment-topic-1";

  @Value("${kafka.topic.in.oai}")
  private final String inTopicOai = "challenge-store-event-2";

  /**
   * Listener for receiving messages from Kafka Topic from Inquisitve
   *
   * @param message the message
   * @throws JsonProcessingException on failing to read.
   */
  @KafkaListener(
      groupId = groupId,
      topics = inTopicInq,
      containerFactory = KAFKA_LISTENER_CONTAINER_FACTORY,
  autoStartup = "${kafka.startup}")
  public void receiveFromInqusitve(String message) throws JsonProcessingException {
    final UserProfileRequest profile = mapper.readValue(message, UserProfileRequest.class);
    log.info("Json message received using Kafka listener {}", profile);
    try {
      coreService.evaluateUserAssessments(profile);
    } catch (Exception ex) {
      log.info("Failed processing the Kafka Message for User Assessment");
    }
  }

  /**
   * Listener for receiving messages from Kafka Topic from Inquisitve
   *
   * @param message the message
   * @throws JsonProcessingException on failing to read.
   */
  @KafkaListener(
      groupId = groupId,
      topics = inTopicOai,
      containerFactory = KAFKA_LISTENER_CONTAINER_FACTORY,
          autoStartup = "${kafka.startup}")
  public void receiveFromOpenAiProcessor(String message) throws JsonProcessingException {
    final KafkaSkillsRequest request = mapper.readValue(message, KafkaSkillsRequest.class);
    log.info("Json message received using Kafka listener {}", request);
    try {
      service.createAssessment(request.getAssessmentRequest());
    } catch (Exception ex) {
      log.info("Failed processing the Kafka Message for User Assessment");
    }
  }
}
