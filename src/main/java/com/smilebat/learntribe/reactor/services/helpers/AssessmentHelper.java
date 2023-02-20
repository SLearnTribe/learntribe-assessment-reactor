package com.smilebat.learntribe.reactor.services.helpers;

import com.smilebat.learntribe.assessment.AssessmentRequest;
import com.smilebat.learntribe.dataaccess.jpa.entity.Assessment;
import com.smilebat.learntribe.dataaccess.jpa.entity.AstChallengeReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.Challenge;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserAstReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserObReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.WorkQueue;
import com.smilebat.learntribe.enums.AssessmentDifficulty;
import com.smilebat.learntribe.enums.AssessmentStatus;
import com.smilebat.learntribe.enums.HiringStatus;
import com.smilebat.learntribe.enums.QueueStatus;
import com.smilebat.learntribe.enums.UserAstReltnType;
import com.smilebat.learntribe.enums.UserObReltnType;
import com.smilebat.learntribe.kafka.KafkaSkillsRequest;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Assessment Service Helper methods.
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Component
public class AssessmentHelper {

  private Assessment createSystemAssessment(String skill, AssessmentDifficulty difficulty) {
    Assessment assessment = new Assessment();
    assessment.setTitle(skill.toUpperCase().trim());
    assessment.setDifficulty(difficulty);
    assessment.setDescription("Recommended");
    assessment.setCreatedBy("SYSTEM");
    return assessment;
  }

  /**
   * Helper methods to create default Assessment entities.
   *
   * @param skill the User Skill.
   * @return the List of {@link Assessment} entities.
   */
  public List<Assessment> createDefaultAssessments(String skill) {
    return List.of(createSystemAssessment(skill, AssessmentDifficulty.BEGINNER));
    // createSystemAssessment(skill, AssessmentDifficulty.INTERMEDIATE));
  }

  /**
   * Creates a User Assessment relation object for HR.
   *
   * @param userId the keyCloak user Id
   * @param assessment the Assessment to be assigned
   * @return the {@link UserAstReltn}
   */
  public UserAstReltn createUserAstReltnForCandidate(String userId, Assessment assessment) {
    UserAstReltn userAstReltn = new UserAstReltn();
    userAstReltn.setUserId(userId);
    userAstReltn.setAssessmentId(assessment.getId());
    userAstReltn.setAssessmentTitle(assessment.getTitle());
    userAstReltn.setStatus(AssessmentStatus.PENDING);
    userAstReltn.setUserAstReltnType(UserAstReltnType.ASSIGNED);
    return userAstReltn;
  }

  /**
   * Creates a User Assessment relation object for HR.
   *
   * @param userId the keyCloak user Id
   * @param assessment the Assessment to be assigned
   * @return the {@link UserAstReltn}
   */
  public UserAstReltn createUserAstReltnForHr(String userId, Assessment assessment) {
    UserAstReltn userAstReltn = new UserAstReltn();
    userAstReltn.setUserId(userId);
    userAstReltn.setAssessmentId(assessment.getId());
    userAstReltn.setAssessmentTitle(assessment.getTitle());
    userAstReltn.setStatus(AssessmentStatus.DEFAULT);
    userAstReltn.setUserAstReltnType(UserAstReltnType.CREATED);
    return userAstReltn;
  }

  /**
   * Creates a user job relation entity.
   *
   * @param candidateId the candidate id
   * @param jobId the job id
   * @return the {@link UserObReltn}
   */
  public UserObReltn createUserObReltn(String candidateId, Long jobId) {
    UserObReltn userObReltn = new UserObReltn();
    userObReltn.setUserObReltn(UserObReltnType.CANDIDATE);
    userObReltn.setHiringStatus(HiringStatus.IN_PROGRESS);
    userObReltn.setUserId(candidateId);
    userObReltn.setJobId(jobId);
    return userObReltn;
  }

  /**
   * Creates a Assesment challenge reltn entity.
   *
   * @param freshAssessment the Assessment
   * @param challenge the Challenge.
   * @return the {@link AstChallengeReltn}.
   */
  public AstChallengeReltn createAstChallengeReltn(
      Assessment freshAssessment, Challenge challenge) {
    AstChallengeReltn astChallengeReltn = new AstChallengeReltn();
    astChallengeReltn.setAssessmentId(freshAssessment.getId());
    astChallengeReltn.setChallengeId(challenge.getId());
    return astChallengeReltn;
  }

  /**
   * Creayes a list of {@link AstChallengeReltn}
   *
   * @param freshAssessment the {@link Assessment}
   * @param challenges the List of {@link Challenge}
   * @return the list of {@link AstChallengeReltn}.
   */
  public Set<AstChallengeReltn> createAstChallengeReltns(
      Assessment freshAssessment, Collection<Challenge> challenges) {
    return challenges
        .stream()
        .map(ch -> createAstChallengeReltn(freshAssessment, ch))
        .collect(Collectors.toSet());
  }

  /**
   * Creates a kafka request for open ai processor.
   *
   * @param request the {@link AssessmentRequest}.
   * @param skill the Skill.
   * @return the {@link KafkaSkillsRequest}.
   */
  public KafkaSkillsRequest getKafkaSkillsRequest(AssessmentRequest request, String skill) {
    KafkaSkillsRequest kafkaSkillsRequest = new KafkaSkillsRequest();
    kafkaSkillsRequest.setSkills(Set.of(skill));
    kafkaSkillsRequest.setAssessmentRequest(request);
    return kafkaSkillsRequest;
  }

  /**
   * Creates a work queue item.
   *
   * @param candidateId the String.
   * @param skills the Set of Skills.
   * @return the {@link WorkQueue}.
   */
  public WorkQueue getSystemWorkQueue(String candidateId, Set<String> skills) {
    WorkQueue queue = new WorkQueue();
    queue.setCreatedFor(candidateId);
    queue.setCreatedBy("SYSTEM");
    queue.setSkills(String.join(",", skills));
    queue.setStatus(QueueStatus.PENDING);
    queue.setDifficulty(AssessmentDifficulty.BEGINNER);
    return queue;
  }

  /**
   * Creates a work queue item.
   *
   * @param candidateId the IAM id of candidate.
   * @param hrId the IAM id of hr.
   * @param skills the Set of Skills.
   * @return the {@link WorkQueue}.
   */
  public WorkQueue getHrWorkQueue(String candidateId, String hrId, Set<String> skills) {
    WorkQueue queue = new WorkQueue();
    queue.setCreatedFor(candidateId);
    queue.setCreatedBy(hrId);
    queue.setSkills(String.join(",", skills));
    queue.setStatus(QueueStatus.PENDING);
    queue.setDifficulty(AssessmentDifficulty.BEGINNER);
    return queue;
  }

  //  /**
  //   * Creates assessment for based on work queue.
  //   *
  //   * @param queueItem the {@link WorkQueue}
  //   * @param skill the skill
  //   * @param challenges the set of {@link Challenge}
  //   * @return the {@link Assessment}.
  //   */
  //  public Assessment createAssessmentForWorkQueue(
  //      WorkQueue queueItem, String skill, Set<Challenge> challenges) {
  //    Assessment assessment = new Assessment();
  //    assessment.setDifficulty(queueItem.getDifficulty());
  //    assessment.setCreatedBy(queueItem.getCreatedBy());
  //    assessment.setStatus(AssessmentStatus.PENDING);
  //    assessment.setTitle(skill);
  //    assessment.setType(AssessmentType.OBJECTIVE);
  //    assessment.setQuestions(challenges.size());
  //    return assessment;
  //  }
}
