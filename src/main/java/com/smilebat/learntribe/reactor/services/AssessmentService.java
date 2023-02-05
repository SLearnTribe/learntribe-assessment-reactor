package com.smilebat.learntribe.reactor.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.smilebat.learntribe.assessment.AssessmentRequest;
import com.smilebat.learntribe.assessment.SubmitAssessmentRequest;
import com.smilebat.learntribe.assessment.SubmitChallengeRequest;
import com.smilebat.learntribe.assessment.response.AssessmentResponse;
import com.smilebat.learntribe.assessment.response.ChallengeResponse;
import com.smilebat.learntribe.dataaccess.AssessmentRepository;
import com.smilebat.learntribe.dataaccess.AssessmentSearchRepository;
import com.smilebat.learntribe.dataaccess.AstChallengeReltnRepository;
import com.smilebat.learntribe.dataaccess.ChallengeRepository;
import com.smilebat.learntribe.dataaccess.UserAstReltnRepository;
import com.smilebat.learntribe.dataaccess.UserObReltnRepository;
import com.smilebat.learntribe.dataaccess.UserProfileRepository;
import com.smilebat.learntribe.dataaccess.jpa.entity.Assessment;
import com.smilebat.learntribe.dataaccess.jpa.entity.AstChallengeReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.Challenge;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserAstReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserObReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserProfile;
import com.smilebat.learntribe.enums.AssessmentDifficulty;
import com.smilebat.learntribe.enums.AssessmentStatus;
import com.smilebat.learntribe.enums.AssessmentType;
import com.smilebat.learntribe.enums.HiringStatus;
import com.smilebat.learntribe.enums.UserObReltnType;
import com.smilebat.learntribe.inquisitve.JobRequest;
import com.smilebat.learntribe.inquisitve.response.OthersBusinessResponse;
import com.smilebat.learntribe.kafka.KafkaSkillsRequest;
import com.smilebat.learntribe.reactor.converters.AssessmentConverter;
import com.smilebat.learntribe.reactor.converters.ChallengeConverter;
import com.smilebat.learntribe.reactor.kafka.KafkaProducer;
import com.smilebat.learntribe.reactor.services.helpers.AssessmentHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.transaction.Transactional;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Assessment Service to hold the business logic.
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

  private final AssessmentRepository assessmentRepository;
  private final AssessmentConverter assessmentConverter;
  private final ChallengeConverter challengeConverter;
  private final AssessmentHelper helper;
  private final UserAstReltnRepository userAstReltnRepository;
  private final UserObReltnRepository userObReltnRepository;
  private final ChallengeRepository challengeRepository;

  private final AstChallengeReltnRepository astChallengeReltnRepository;

  private final UserProfileRepository userProfileRepository;

  private final AssessmentSearchRepository assessmentSearchRepository;

  /*Kafka Messaging*/
  private final KafkaProducer kafka;

  private final ObjectMapper mapper;

  private static final String[] ASSESSMENT_STATUS_FILTERS =
      Arrays.stream(AssessmentStatus.values())
          .map(AssessmentStatus::name)
          .toArray(s -> new String[s]);

  /** Assessment pagination concept builder. */
  @Getter
  @Setter
  @Builder
  public static class PageableAssessmentRequest {
    private String keyCloakId;
    private String[] filters;
    private Pageable paging;
  }

  /**
   * Retrieves all previous generated assessment for HR.
   *
   * @param keyCloakId the hr IAM id.
   * @return the list of {@link AssessmentResponse}.
   */
  @Transactional
  public List<AssessmentResponse> getGeneratedAssessments(String keyCloakId) {
    Verify.verifyNotNull(keyCloakId, "User Keycloak Id cannnot be null");

    List<UserAstReltn> userAstReltns = userAstReltnRepository.findByUserId(keyCloakId);
    if (userAstReltns == null || userAstReltns.isEmpty()) {
      return Collections.emptyList();
    }

    final List<Long> assessmentIds =
        userAstReltns.stream().map(UserAstReltn::getAssessmentId).collect(Collectors.toList());
    final Iterable<Assessment> assessments = assessmentRepository.findAllById(assessmentIds);
    return assessmentConverter.toResponse(ImmutableList.copyOf(assessments));
  }

  /**
   * Assings an existing assessment to candidate.
   *
   * @param userId the HR user id
   * @param assigneeEmail the candidate email id.
   * @param assessmentId the assessment to be assigned.
   * @return true/false.
   */
  @Transactional
  public boolean assignAssessment(String userId, String assigneeEmail, Long assessmentId) {
    Verify.verifyNotNull(userId, "User Keycloak Id cannnot be null");
    Verify.verifyNotNull(assigneeEmail, "Assignee email Id cannnot be null");
    Verify.verifyNotNull(assessmentId, "Assessment Id cannnot be null");

    Optional<Assessment> assessment = assessmentRepository.findById(assessmentId);
    if (!assessment.isPresent()) {
      return false;
    }

    UserProfile candidateProfile = userProfileRepository.findByEmail(assigneeEmail);

    if (candidateProfile == null) {
      return false;
    }

    final String candidateId = candidateProfile.getKeyCloakId();
    final UserAstReltn userAstReltnForCandidate =
        helper.createUserAstReltnForCandidate(candidateId, assessment.get());
    userAstReltnRepository.save(userAstReltnForCandidate);
    return true;
  }

  /**
   * Retrieves user & skill related assessments.
   *
   * @param request the {@link PageableAssessmentRequest} the ID provided by IAM (keycloak)
   * @param keyword the search term.
   * @return the List of {@link AssessmentResponse}
   */
  @Transactional
  @Nullable
  public List<AssessmentResponse> retrieveUserAssessments(
      PageableAssessmentRequest request, String keyword) throws InterruptedException {
    String keyCloakId = request.getKeyCloakId();
    Verify.verifyNotNull(keyCloakId, "User Keycloak Id cannnot be null");
    log.info("Fetching Assessments for User {}", keyCloakId);
    Pageable paging = request.getPaging();
    String[] filters = evaluateAssessmentStatusFilters(request);
    List<UserAstReltn> userAstReltns =
        getUserAssessmentRelations(keyword, keyCloakId, paging, filters);
    List<Assessment> assessments = fetchExisitingAssessments(userAstReltns);
    List<AssessmentResponse> responses = assessmentConverter.toResponse(assessments);
    mapUserAssessmentStatus(userAstReltns, responses);
    return responses;
  }

  private List<UserAstReltn> getUserAssessmentRelations(
      String keyword, String keyCloakId, Pageable paging, String[] filters)
      throws InterruptedException {
    if (keyword == null || keyword.isEmpty()) {
      return userAstReltnRepository.findByUserIdAndFilter(keyCloakId, filters, paging);
    }
    try {
      return assessmentSearchRepository.search(keyword, filters, keyCloakId, paging);
    } catch (InterruptedException ex) {
      log.info("No Assessments related to search keyword {}", keyword);
      throw ex;
    }
  }

  private List<Assessment> fetchExisitingAssessments(List<UserAstReltn> userAstReltns) {
    final Long[] assessmentIds =
        userAstReltns.stream().map(UserAstReltn::getAssessmentId).toArray(s -> new Long[s]);
    return assessmentRepository.findAllByIds(assessmentIds);
  }

  private void mapUserAssessmentStatus(
      Collection<UserAstReltn> userAstReltns, Collection<AssessmentResponse> responses) {
    for (UserAstReltn userAstReltn : userAstReltns) {
      if (userAstReltn.getStatus() != null) {
        responses
            .stream()
            .filter(response -> response.getId() == userAstReltn.getAssessmentId())
            .forEach(response -> response.setStatus(userAstReltn.getStatus().name()));
      }
    }
  }

  /**
   * Evaluates Assessment Status filters.
   *
   * @param request the {@link PageableAssessmentRequest}
   * @return the array of {@link String} filters
   */
  private String[] evaluateAssessmentStatusFilters(PageableAssessmentRequest request) {
    String[] filters = request.getFilters();
    return filters != null && filters.length > 0 ? filters : ASSESSMENT_STATUS_FILTERS;
  }

  /**
   * Retrieves assessment with challenges.
   *
   * @param assessmentId the Assessment id.
   * @return AssessmentResponse the {@link AssessmentResponse}.
   */
  @Transactional
  public AssessmentResponse retrieveAssessment(Long assessmentId) {
    Verify.verifyNotNull(assessmentId, "Assessment ID cannnot be null");
    log.info("Fetching Assessments with id {}", assessmentId);
    Assessment assessment = assessmentRepository.findByAssessmentId(assessmentId);
    if (assessment == null) {
      log.info("No Assessment found");
      return new AssessmentResponse();
    }
    final Set<AstChallengeReltn> astChallengeReltns =
        astChallengeReltnRepository.findByAssessmentId(assessmentId);
    final Set<Long> challengeIds =
        astChallengeReltns
            .stream()
            .map(AstChallengeReltn::getChallengeId)
            .collect(Collectors.toSet());
    final List<Challenge> challenges = challengeRepository.findAllById(challengeIds);
    AssessmentResponse assessmentResponse = assessmentConverter.toResponse(assessment);
    List<ChallengeResponse> challengeResponses = challengeConverter.toResponse(challenges);
    if (challengeResponses != null && !challengeResponses.isEmpty()) {
      assessmentResponse.setChallengeResponses(challengeResponses);
    }
    return assessmentResponse;
  }

  /**
   * Submits the user assessment.
   *
   * @param request the {@link SubmitAssessmentRequest}.
   */
  @Transactional
  public void submitAssessment(SubmitAssessmentRequest request) {
    Verify.verifyNotNull(request, "Request cannot be null");
    final Long assessmentId = request.getId();
    Verify.verifyNotNull(assessmentId, "Assessment Id cannot be null");
    List<SubmitChallengeRequest> challengeResponses = request.getChallengeResponses();
    Verify.verifyNotNull(challengeResponses, "Challenges cannot be null");
    final String keyCloakId = request.getKeyCloakId();
    final Optional<Assessment> byAssessmentId = assessmentRepository.findById(assessmentId);

    if (!byAssessmentId.isPresent()) {
      throw new IllegalArgumentException("Invalid Assessment or Assessment not present");
    }

    List<Long> challengeIds =
        challengeResponses.stream().map(SubmitChallengeRequest::getId).collect(Collectors.toList());

    final List<Challenge> challenges = challengeRepository.findAllById(challengeIds);

    float totalCorrectAnswers = 0;
    for (Challenge challenge : challenges) {
      final String answer = challenge.getAnswer();
      final Long id = challenge.getId();
      boolean isCorrectAnswer =
          challengeResponses
              .stream()
              .filter(req -> id.equals(req.getId()))
              .anyMatch(req -> answer.equals(req.getAnswer()));
      if (isCorrectAnswer) {
        totalCorrectAnswers += 1;
      }
    }

    Set<AstChallengeReltn> astChallengeReltns =
        astChallengeReltnRepository.findByAssessmentId(assessmentId);
    float totalQuestions = astChallengeReltns.size();

    float passPercentage = (totalCorrectAnswers * 100) / totalQuestions;
    UserAstReltn userAstReltn = userAstReltnRepository.findByUserAstReltn(keyCloakId, assessmentId);
    userAstReltn.setStatus(AssessmentStatus.FAILED);
    if (passPercentage > 65.00f) {
      userAstReltn.setStatus(AssessmentStatus.COMPLETED);
    }
    userAstReltnRepository.save(userAstReltn);
  }

  /**
   * Creates a assessment as per the requirements.
   *
   * @param request the {@link JobRequest}
   * @return the {@link OthersBusinessResponse}.
   */
  @Transactional
  public boolean createAssessment(AssessmentRequest request) {
    String hrId = request.getAssignedBy();
    Verify.verifyNotNull(hrId, "User Id cannot be null");
    Verify.verifyNotNull(request, "Job Request cannot be null");

    String title = request.getTitle();
    List<String> candidateEmails = request.getAssigneeEmails();

    List<UserProfile> allUsersByEmail =
        userProfileRepository.findAllByEmail(candidateEmails.stream().toArray(s -> new String[s]));
    if (allUsersByEmail == null || allUsersByEmail.isEmpty()) {
      throw new IllegalArgumentException("Invalid Assignee Emails :No Users found");
    }

    List<String> candidateIds =
        allUsersByEmail.stream().map(UserProfile::getKeyCloakId).collect(Collectors.toList());

    String[] skills = title.split(",");
    Long relatedJobId = request.getRelatedJobId();

    for (String skill : skills) {
      AssessmentDifficulty difficulty = request.getDifficulty();
      Optional<Assessment> existingHrAssessment =
          assessmentRepository.findByUserTitleDifficulty(
              hrId, skill.toUpperCase().trim(), difficulty.name());

      if (existingHrAssessment.isPresent()) {
        assignExistingAssessment(candidateIds, existingHrAssessment.get());
      } else {
        createFreshAssessment(request, candidateIds, skill);
      }

      createUsersJobReltn(candidateIds, relatedJobId);
    }

    return true;
  }

  private void createFreshAssessment(
      AssessmentRequest request, List<String> candidateIds, String skill) {
    final AssessmentDifficulty difficulty = request.getDifficulty();
    final String hrId = request.getAssignedBy();

    // Get 15 challenges randomly
    final Set<Challenge> challenges =
        challengeRepository.findBySkill(skill.toUpperCase().trim(), difficulty.name());

    if (challenges.isEmpty()) {
      log.info("Requesting Challenge Store for : {}",skill);
      KafkaSkillsRequest kafkaSkillsRequest = new KafkaSkillsRequest();
      kafkaSkillsRequest.setSkills(Set.of(skill));
      kafkaSkillsRequest.setAssessmentRequest(request);
      // send to open ai processor and generate questions async
      try {
        kafka.sendMessage(mapper.writeValueAsString(kafkaSkillsRequest));
      } catch (JsonProcessingException e) {
        log.info("Failed processing the User Profile for Kafka Streaming");
        throw new RuntimeException(e);
      }
      return;
    }

    log.info("Creating fresh Assessment : Initiated By {}", hrId);
    Assessment freshAssessment = new Assessment();
    freshAssessment.setCreatedBy(hrId);
    freshAssessment.setRelatedJobId(request.getRelatedJobId());
    freshAssessment.setTitle(skill.toUpperCase().trim());
    freshAssessment.setDifficulty(difficulty);
    freshAssessment.setType(AssessmentType.OBJECTIVE);
    assessmentRepository.save(freshAssessment);

    createAstChallengeReltn(challenges, freshAssessment);
    createUserAstReltn(candidateIds, hrId, freshAssessment);
  }

  private void createAstChallengeReltn(Set<Challenge> challenges, Assessment freshAssessment) {
    List<AstChallengeReltn> astChallengeReltnList = new ArrayList<>(challenges.size());
    for (Challenge challenge : challenges) {
      AstChallengeReltn astChallengeReltn = new AstChallengeReltn();
      astChallengeReltn.setAssessmentId(freshAssessment.getId());
      astChallengeReltn.setChallengeId(challenge.getId());
      astChallengeReltnList.add(astChallengeReltn);
    }
    astChallengeReltnRepository.saveAll(astChallengeReltnList);
  }

  private void createUserAstReltn(
      List<String> candidateIds, String hrId, Assessment freshAssessment) {
    final List<UserAstReltn> userAstReltns =
        candidateIds
            .stream()
            .map(candidateId -> helper.createUserAstReltnForCandidate(candidateId, freshAssessment))
            .collect(Collectors.toList());
    UserAstReltn userAstReltnForHr = helper.createUserAstReltnForHr(hrId, freshAssessment);
    userAstReltns.add(userAstReltnForHr);
    userAstReltnRepository.saveAll(userAstReltns);
  }

  @Transactional
  private void createUsersJobReltn(Collection<String> candidateIds, Long jobId) {
    final List<UserObReltn> userObReltns =
        candidateIds
            .stream()
            .filter(
                candidateId -> userObReltnRepository.findByRelatedJobId(candidateId, jobId) == null)
            .map(candidateId -> createUserObReltn(candidateId, jobId))
            .collect(Collectors.toList());
    if (!userObReltns.isEmpty()) {
    userObReltnRepository.saveAll(userObReltns);
    }
  }

  /**
   * Creates a user job relation entity.
   *
   * @param candidateId the candidate id
   * @param jobId the job id
   * @return the {@link UserObReltn}
   */
  private UserObReltn createUserObReltn(String candidateId, Long jobId) {
    UserObReltn userObReltn = new UserObReltn();
    userObReltn.setUserObReltn(UserObReltnType.CANDIDATE);
    userObReltn.setHiringStatus(HiringStatus.IN_PROGRESS);
    userObReltn.setUserId(candidateId);
    userObReltn.setJobId(jobId);
    return userObReltn;
  }

  /**
   * ASsigns existing assessments to the users.
   *
   * @param candidateIds the array of candidates.
   * @param hrAssessment the assessment created by hr.
   */
  private void assignExistingAssessment(Collection<String> candidateIds, Assessment hrAssessment) {
    Long hrAssessmentId = hrAssessment.getId();
    log.info("Assigning existing assessments");

    /*Validate if the Candidate is already assigned with the assessment*/
    List<UserAstReltn> userAstReltns =
        userAstReltnRepository.findAllByUserAstReltn(
            candidateIds.stream().toArray(s -> new String[s]), hrAssessmentId);

    List<UserAstReltn> userAstReltnCandidateList = new ArrayList<>();

    for (String candidateId : candidateIds) {
      final boolean isAssigned =
          userAstReltns
              .stream()
              .anyMatch(userAstReltn -> candidateId.equals(userAstReltn.getUserId()));
      if (!isAssigned) {
        UserAstReltn userAstReltnForCandidate =
            helper.createUserAstReltnForCandidate(candidateId, hrAssessment);
        userAstReltnCandidateList.add(userAstReltnForCandidate);
      }
    }
    userAstReltnRepository.saveAll(userAstReltnCandidateList);
  }
}
