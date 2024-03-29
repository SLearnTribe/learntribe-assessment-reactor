package com.smilebat.learntribe.reactor.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Verify;
import com.smilebat.learntribe.dataaccess.AssessmentRepository;
import com.smilebat.learntribe.dataaccess.AstChallengeReltnRepository;
import com.smilebat.learntribe.dataaccess.ChallengeRepository;
import com.smilebat.learntribe.dataaccess.UserAstReltnRepository;
import com.smilebat.learntribe.dataaccess.WorkQueueRepository;
import com.smilebat.learntribe.dataaccess.jpa.entity.Assessment;
import com.smilebat.learntribe.dataaccess.jpa.entity.Challenge;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserAstReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.WorkQueue;
import com.smilebat.learntribe.enums.AssessmentDifficulty;
import com.smilebat.learntribe.inquisitve.UserProfileRequest;
import com.smilebat.learntribe.kafka.KafkaSkillsRequest;
import com.smilebat.learntribe.reactor.kafka.KafkaProducer;
import com.smilebat.learntribe.reactor.services.helpers.AssessmentHelper;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Core Assessment Service to hold the Open Assessment creation business logic.
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoreAssessmentService {

  private final AssessmentRepository assessmentRepository;

  private final ChallengeRepository challengeRepository;

  private final AstChallengeReltnRepository astChallengeReltnRepository;

  private final UserAstReltnRepository userAstReltnRepository;

  private final AssessmentHelper helper;

  private final WorkQueueRepository workQueueRepository;

  /*Kafka Messaging*/
  private final KafkaProducer kafka;

  private final ObjectMapper mapper;

  /**
   * Evaluates and Creates new User Assessments.
   *
   * @param profile the User Profile.
   */
  @Transactional
  public void evaluateUserAssessments(UserProfileRequest profile) {
    Verify.verifyNotNull(profile, "User Profile cannot be null");
    String candidateId = profile.getKeyCloakId();
    Verify.verifyNotNull(candidateId, "Candidate Id cannot be null");
    log.info("Evaluating Assessments for User {}", candidateId);
    final List<UserAstReltn> userAstReltns = userAstReltnRepository.findByUserId(candidateId);
    Set<String> userSkills = evaluateUserSkills(profile, userAstReltns);
    /*Validate if user added new skill*/
    if (!userSkills.isEmpty()) {
      createFreshUserAssessments(candidateId, userSkills);
    }
  }

  private Set<String> getUpdatedUserSkills(
      Set<String> userSkills, List<UserAstReltn> userAstReltns) {
    log.info("Fetching Updated User skills");
    return userSkills
        .stream()
        .filter(skill -> !isAssessmentPresent(userAstReltns, skill))
        .collect(Collectors.toSet());
  }

  private boolean isAssessmentPresent(Collection<UserAstReltn> userAstReltns, String skill) {
    return userAstReltns
        .stream()
        .anyMatch(reltn -> skill.trim().equalsIgnoreCase(reltn.getAssessmentTitle().trim()));
  }

  private void createFreshUserAssessments(String candidateId, Set<String> userSkills) {
    log.info("Creating Default User Assessments for the skills {}", userSkills);
    final List<Assessment> defaultAssessments = getDefaultAssessments(userSkills);
    evaluateChallenges(candidateId, defaultAssessments);
  }

  private List<Assessment> getDefaultAssessments(Set<String> userSkills) {
    log.info("Fetching System Default Assessments for the New Skill");
    return userSkills
        .stream()
        .map(helper::createDefaultAssessments)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  @SneakyThrows
  private void evaluateChallenges(String candidateId, List<Assessment> defaultAssessments) {
    Set<String> skills = new HashSet<>();

    for (Assessment assessment : defaultAssessments) {
      log.info("Creating fresh assessment for User {}", candidateId);
      final String skill = assessment.getTitle();
      final AssessmentDifficulty difficulty = assessment.getDifficulty();
      Set<Challenge> freshChallenges = challengeRepository.findBySkill(skill, difficulty.name());
      if (!freshChallenges.isEmpty() && freshChallenges.size() > 10) {
        assessment.setQuestions(freshChallenges.size());
        assessmentRepository.save(assessment);
        astChallengeReltnRepository.saveAll(
            helper.createAstChallengeReltns(assessment, freshChallenges));
        Long assessmentId = assessment.getId();
        createUserAssessmentRelation("SYSTEM", List.of(candidateId), assessmentId);
        log.info("Successfuly created assessment {} for User {}", assessmentId, candidateId);
      } else {
        skills.add(skill);
      }
    }
    if (!skills.isEmpty()) {
      WorkQueue workQueue = workQueueRepository.findBySystemCreated(candidateId);
      if (workQueue != null) {
        String queueSkills = workQueue.getSkills();
        String newSkills =
            skills.stream().filter(s -> !queueSkills.contains(s)).collect(Collectors.joining(","));
        if (!newSkills.isEmpty()) {
          workQueue.setSkills(String.join(",", newSkills, queueSkills));
        }
      } else {
        workQueue = helper.getSystemWorkQueue(candidateId, skills);
      }
      workQueueRepository.save(workQueue);
      KafkaSkillsRequest kafkaRequest = getKafkaSkillsRequest(skills);
      kafka.sendMessage(mapper.writeValueAsString(kafkaRequest));
    }
  }

  @NotNull
  private KafkaSkillsRequest getKafkaSkillsRequest(Set<String> skills) {
    KafkaSkillsRequest kafkaRequest = new KafkaSkillsRequest();
    kafkaRequest.setSkills(skills);
    return kafkaRequest;
  }

  private Set<String> evaluateUserSkills(
      UserProfileRequest userProfile, List<UserAstReltn> userAstReltns) {
    log.info("Evaluating User Skills");
    String skills = userProfile.getSkills();
    if (skills == null || skills.isEmpty()) {
      return Collections.emptySet();
    }
    boolean hasUserAssessments = userAstReltns != null && !userAstReltns.isEmpty();
    Set<String> userSkills = Arrays.stream(skills.split(",")).collect(Collectors.toSet());
    final Set<String> updatedUserSkills = getUpdatedUserSkills(userSkills, userAstReltns);
    if (hasUserAssessments && updatedUserSkills.isEmpty()) {
      log.info("All System Assessments already present");
      return Collections.emptySet();
    }
    return hasUserAssessments ? updatedUserSkills : userSkills;
  }

  /**
   * Creates user and assessment relation for fresh assessment
   *
   * @param hrId the HR user id
   * @param candidateIds the candidate id list
   * @param assessmentId the new assessment id
   */
  public void createUserAssessmentRelation(
      String hrId, Collection<String> candidateIds, Long assessmentId) {
    final Optional<Assessment> pAssessment = assessmentRepository.findById(assessmentId);
    if (!pAssessment.isPresent()) {
      throw new IllegalArgumentException();
    }
    Assessment assessment = pAssessment.get();
    final List<UserAstReltn> userAstReltns =
        candidateIds
            .stream()
            .map(
                candidateId ->
                    UserAstReltn.create(
                        candidateId, assessment, UserAstReltn::applyReltnForCandidate))
            .collect(Collectors.toList());
    UserAstReltn userAstReltnForHr =
        UserAstReltn.create(hrId, assessment, UserAstReltn::applyReltnForHr);
    userAstReltns.add(userAstReltnForHr);
    userAstReltnRepository.saveAll(userAstReltns);
  }
}
