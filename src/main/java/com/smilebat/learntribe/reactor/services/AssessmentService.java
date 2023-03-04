package com.smilebat.learntribe.reactor.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.smilebat.learntribe.assessment.AssessmentRequest;
import com.smilebat.learntribe.assessment.SubmitAssessmentRequest;
import com.smilebat.learntribe.assessment.SubmitChallengeRequest;
import com.smilebat.learntribe.assessment.response.AssessmentResponse;
import com.smilebat.learntribe.assessment.response.ChallengeResponse;
import com.smilebat.learntribe.assessment.response.SubmitAssessmentResponse;
import com.smilebat.learntribe.dataaccess.AssessmentRepository;
import com.smilebat.learntribe.dataaccess.AssessmentSearchRepository;
import com.smilebat.learntribe.dataaccess.AstChallengeReltnRepository;
import com.smilebat.learntribe.dataaccess.ChallengeRepository;
import com.smilebat.learntribe.dataaccess.OthersBusinessRepository;
import com.smilebat.learntribe.dataaccess.UserAstReltnRepository;
import com.smilebat.learntribe.dataaccess.UserObReltnRepository;
import com.smilebat.learntribe.dataaccess.UserProfileRepository;
import com.smilebat.learntribe.dataaccess.WorkQueueRepository;
import com.smilebat.learntribe.dataaccess.jpa.entity.Assessment;
import com.smilebat.learntribe.dataaccess.jpa.entity.AstChallengeReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.Challenge;
import com.smilebat.learntribe.dataaccess.jpa.entity.OthersBusiness;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserAstReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserObReltn;
import com.smilebat.learntribe.dataaccess.jpa.entity.UserProfile;
import com.smilebat.learntribe.dataaccess.jpa.entity.WorkQueue;
import com.smilebat.learntribe.enums.AssessmentDifficulty;
import com.smilebat.learntribe.enums.AssessmentStatus;
import com.smilebat.learntribe.enums.AssessmentType;
import com.smilebat.learntribe.inquisitve.JobRequest;
import com.smilebat.learntribe.inquisitve.response.OthersBusinessResponse;
import com.smilebat.learntribe.kafka.KafkaSkillsRequest;
import com.smilebat.learntribe.learntribevalidator.learntribeexceptions.InvalidDataException;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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

  private final WorkQueueRepository workQueueRepository;

  private final OthersBusinessRepository jobRepository;

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
    final UserAstReltn userAstReltn =
        UserAstReltn.create(candidateId, assessment.get(), UserAstReltn::applyReltnForCandidate);
    userAstReltnRepository.save(userAstReltn);
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
    log.info("Validating work queue for {}", keyCloakId);
    List<WorkQueue> queueItems = workQueueRepository.findByUserId(keyCloakId);
    queueItems.forEach(this::evaluateQueuedAssessments);
    log.info("Fetching Assessments for User {}", keyCloakId);
    Pageable paging = request.getPaging();
    String[] filters = evaluateAssessmentStatusFilters(request);
    List<UserAstReltn> userAstReltns =
        getUserAssessmentRelations(keyword, keyCloakId, paging, filters);

    /* If user assessment status is in progress move it to blocked*/
    userAstReltns
        .stream()
        .filter(reltn -> AssessmentStatus.IN_PROGRESS == reltn.getStatus())
        .forEach(reltn -> reltn.setStatus(AssessmentStatus.BLOCKED));

    userAstReltnRepository.saveAll(userAstReltns);

    List<Assessment> assessments = fetchExisitingAssessments(userAstReltns);
    List<AssessmentResponse> responses = assessmentConverter.toResponse(assessments);
    mapBusinessName(responses);
    mapUserAssessmentStatus(userAstReltns, responses);
    return responses;
  }

  private void mapBusinessName(List<AssessmentResponse> responses) {
    for (AssessmentResponse response : responses) {
      final long id = response.getRelatedJobId();
      final Optional<OthersBusiness> byBusiness = jobRepository.findById(id);
      if (byBusiness.isPresent()) {
        OthersBusiness business = byBusiness.get();
        response.setBusinessName(business.getBusinessName());
      }
    }
  }

  private void evaluateQueuedAssessments(WorkQueue queueItem) {
    String skills = queueItem.getSkills();
    String candidateId = queueItem.getCreatedFor();
    if (skills == null || skills.isEmpty()) {
      log.info("no work item skills found for the user {}", candidateId);
      return;
    }
    String[] parsedSkills = skills.split(",");
    for (String skill : parsedSkills) {
      final Optional<Assessment> byUserTitleDifficulty =
          assessmentRepository.findByUserTitleDifficulty(
              candidateId, skill, queueItem.getDifficulty().name());
      if (byUserTitleDifficulty.isPresent()) {
        log.info("Assessment already present for the user from queue");
        workQueueRepository.delete(queueItem);
        return;
      }
      final Set<Challenge> challenges =
          challengeRepository.findBySkill(skill, queueItem.getDifficulty().name());
      if (challenges.size() > 10) {
        Assessment assessment = new Assessment();
        assessment.setDifficulty(queueItem.getDifficulty());
        assessment.setCreatedBy(queueItem.getCreatedBy());
        assessment.setStatus(AssessmentStatus.PENDING);
        assessment.setTitle(skill);
        assessment.setType(AssessmentType.OBJECTIVE);
        assessment.setQuestions(challenges.size());
        assessmentRepository.save(assessment);
        createAstChallengeReltns(challenges, assessment);
        final UserAstReltn userAstReltn =
            UserAstReltn.create(candidateId, assessment, UserAstReltn::applyReltnForCandidate);
        userAstReltnRepository.save(userAstReltn);
        log.info("Successfully created new assessment for the user from work item");
        workQueueRepository.delete(queueItem);
      }
    }
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
   * @param keycloakId the IAM id.
   * @return AssessmentResponse the {@link AssessmentResponse}.
   */
  @Transactional
  public AssessmentResponse retrieveAssessment(String keycloakId, Long assessmentId) {
    Verify.verifyNotNull(assessmentId, "Assessment ID cannnot be null");
    Verify.verifyNotNull(keycloakId, "Keycloak ID cannnot be null");
    log.info("Fetching Assessments with id {}", assessmentId);
    Assessment assessment = assessmentRepository.findByAssessmentId(assessmentId);
    if (assessment == null) {
      log.info("No Assessment found");
      throw new InvalidDataException("Assessment " + assessmentId + " not found");
    }
    final Set<AstChallengeReltn> astChallengeReltns =
        astChallengeReltnRepository.findByAssessmentId(assessmentId);
    final Set<Long> challengeIds =
        astChallengeReltns
            .stream()
            .map(AstChallengeReltn::getChallengeId)
            .collect(Collectors.toSet());

    /*Move Assessment status to in progress*/
    UserAstReltn userAstReltn = userAstReltnRepository.findByUserAstReltn(keycloakId, assessmentId);
    if (AssessmentStatus.BLOCKED != userAstReltn.getStatus()) {
      userAstReltn.setStatus(AssessmentStatus.IN_PROGRESS);
      userAstReltnRepository.save(userAstReltn);
    }

    final List<Challenge> challenges = challengeRepository.findAllById(challengeIds);
    AssessmentResponse assessmentResponse = assessmentConverter.toResponse(assessment);
    List<ChallengeResponse> challengeResponses = challengeConverter.toResponse(challenges);
    if (challengeResponses != null && !challengeResponses.isEmpty()) {
      assessmentResponse.setChallengeResponses(challengeResponses);
      assessmentResponse.setNumOfQuestions(challengeResponses.size());
    }

    assessmentResponse.setReqTimeInMillis((long) challenges.size() * 60 * 1000);
    return assessmentResponse;
  }

  /**
   * Submits the user assessment.
   *
   * @param request the {@link SubmitAssessmentRequest}.
   * @return SubmitAssessmentResponse.
   */
  @Transactional
  public SubmitAssessmentResponse submitAssessment(SubmitAssessmentRequest request) {
    Verify.verifyNotNull(request, "Request cannot be null");
    final Long assessmentId = request.getId();
    Verify.verifyNotNull(assessmentId, "Assessment Id cannot be null");
    List<SubmitChallengeRequest> challengeResponses = request.getChallengeResponses();
    Verify.verifyNotNull(challengeResponses, "Challenges cannot be null");
    final String keyCloakId = request.getKeyCloakId();
    final Optional<Assessment> byAssessmentId = assessmentRepository.findById(assessmentId);

    if (!byAssessmentId.isPresent()) {
      throw new InvalidDataException(
          "Invalid Assessment or Assessment " + assessmentId + " not present");
    }

    List<Long> challengeIds =
        challengeResponses.stream().map(SubmitChallengeRequest::getId).collect(Collectors.toList());

    final List<Challenge> challenges = challengeRepository.findAllById(challengeIds);

    int totalCorrectAnswers = 0;
    totalCorrectAnswers = getTotalCorrectAnswers(challengeResponses, challenges);

    UserAstReltn userAstReltn = evaluateUsrAstReltn(assessmentId, keyCloakId);

    Set<AstChallengeReltn> astChallengeReltns =
        astChallengeReltnRepository.findByAssessmentId(assessmentId);

    int totalQuestions = astChallengeReltns.size();
    userAstReltn.setStatus(AssessmentStatus.BLOCKED);
    userAstReltn.setAnswered(totalCorrectAnswers);
    userAstReltn.setQuestions(totalQuestions);

    AssessmentDifficulty difficulty = byAssessmentId.get().getDifficulty();
    evaluateStatus(userAstReltn, difficulty);
    userAstReltnRepository.save(userAstReltn);

    return getSubmitAssessmentResponse(challengeResponses, totalQuestions);
  }

  private int getTotalCorrectAnswers(
      Collection<SubmitChallengeRequest> challengeResponses, Collection<Challenge> challenges) {
    int totalCorrectAnswers = 0;
    for (Challenge challenge : challenges) {
      final String answer = challenge.getAnswer();
      final Long id = challenge.getId();
      totalCorrectAnswers += getAnswerCount(challengeResponses, answer, id);
    }
    return totalCorrectAnswers;
  }

  @NotNull
  private UserAstReltn evaluateUsrAstReltn(Long assessmentId, String keyCloakId) {
    UserAstReltn userAstReltn = userAstReltnRepository.findByUserAstReltn(keyCloakId, assessmentId);
    if (AssessmentStatus.BLOCKED == userAstReltn.getStatus()) {
      throw new InvalidDataException(
          "Assessment " + assessmentId + " is Temporarily Blocked for the User");
    }
    return userAstReltn;
  }

  @NotNull
  private SubmitAssessmentResponse getSubmitAssessmentResponse(
      Collection<SubmitChallengeRequest> challengeResponses, int totalQuestions) {
    SubmitAssessmentResponse response = new SubmitAssessmentResponse();
    response.setTotalQuestions(totalQuestions);
    response.setTotalAnswered(challengeResponses.size());
    return response;
  }

  private void evaluateStatus(UserAstReltn userAstReltn, AssessmentDifficulty difficulty) {
    final int passPercentage = (userAstReltn.getAnswered() * 100) / userAstReltn.getQuestions();
    if (passPercentage > 59 && AssessmentDifficulty.BEGINNER == difficulty) {
      userAstReltn.setStatus(AssessmentStatus.COMPLETED);
    } else if (passPercentage > 64 && AssessmentDifficulty.INTERMEDIATE == difficulty) {
      userAstReltn.setStatus(AssessmentStatus.COMPLETED);
    }
  }

  private long getAnswerCount(
      Collection<SubmitChallengeRequest> challengeResponses, String answer, Long id) {
    return challengeResponses
        .stream()
        .filter(req -> id.equals(req.getId()))
        .filter(req -> req.getAnswer() != null)
        .filter(req -> answer.contains(req.getAnswer()))
        .count();
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
      throw new InvalidDataException("Invalid Assignee Emails :No Users found");
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

  @SneakyThrows
  private void createFreshAssessment(
      AssessmentRequest request, List<String> candidateIds, String skill) {
    final AssessmentDifficulty difficulty = request.getDifficulty();
    final String hrId = request.getAssignedBy();
    String upperCaseSkill = skill.toUpperCase().trim();
    // Get 15 challenges randomly
    final Set<Challenge> challenges =
        challengeRepository.findBySkill(upperCaseSkill, difficulty.name());
    if (challenges.isEmpty()) {
      log.info("Requesting Challenge Store for : {}", skill);
      evaluateWorkQueue(candidateIds, hrId, upperCaseSkill);
      KafkaSkillsRequest kafkaSkillsRequest = helper.getKafkaSkillsRequest(request, skill);
      // send to open ai processor and generate questions async
      kafka.sendMessage(mapper.writeValueAsString(kafkaSkillsRequest));
      return;
    }

    log.info("Creating fresh Assessment : Initiated By {}", hrId);
    Assessment freshAssessment = new Assessment();
    freshAssessment.setCreatedBy(hrId);
    freshAssessment.setRelatedJobId(request.getRelatedJobId());
    freshAssessment.setTitle(skill.toUpperCase().trim());
    freshAssessment.setDifficulty(difficulty);
    freshAssessment.setType(AssessmentType.OBJECTIVE);
    freshAssessment.setQuestions(challenges.size());
    assessmentRepository.save(freshAssessment);

    createAstChallengeReltns(challenges, freshAssessment);
    createUsersAstReltn(candidateIds, hrId, freshAssessment);
  }

  private void evaluateWorkQueue(List<String> candidateIds, String hrId, String upperCaseSkill) {
    for (String id : candidateIds) {
      final WorkQueue queueItem = workQueueRepository.findByHrCreated(id, hrId);
      if (queueItem == null) {
        workQueueRepository.save(helper.getHrWorkQueue(id, hrId, Set.of(upperCaseSkill)));
      } else {
        String skills = queueItem.getSkills();
        if (skills != null && !skills.contains(upperCaseSkill)) {
          queueItem.setSkills(String.join(",", upperCaseSkill, skills));
          workQueueRepository.save(queueItem);
        }
      }
    }
  }

  private void createAstChallengeReltns(Set<Challenge> challenges, Assessment freshAssessment) {
    List<AstChallengeReltn> astChallengeReltnList = new ArrayList<>(challenges.size());
    for (Challenge challenge : challenges) {
      AstChallengeReltn astChallengeReltn =
          helper.createAstChallengeReltn(freshAssessment, challenge);
      astChallengeReltnList.add(astChallengeReltn);
    }
    astChallengeReltnRepository.saveAll(astChallengeReltnList);
  }

  @Transactional
  private void createUsersAstReltn(
      List<String> candidateIds, String hrId, Assessment freshAssessment) {
    final List<UserAstReltn> userAstReltns =
        candidateIds
            .stream()
            .map(
                candidateId ->
                    UserAstReltn.create(
                        candidateId, freshAssessment, UserAstReltn::applyReltnForCandidate))
            .collect(Collectors.toList());
    UserAstReltn userAstReltnForHr =
        UserAstReltn.create(hrId, freshAssessment, UserAstReltn::applyReltnForHr);
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
            .map(candidateId -> helper.createUserObReltn(candidateId, jobId))
            .collect(Collectors.toList());
    if (!userObReltns.isEmpty()) {
      userObReltnRepository.saveAll(userObReltns);
    }
  }

  /**
   * Assigns existing assessments to the users.
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
            UserAstReltn.create(candidateId, hrAssessment, UserAstReltn::applyReltnForCandidate);
        userAstReltnCandidateList.add(userAstReltnForCandidate);
      }
    }
    userAstReltnRepository.saveAll(userAstReltnCandidateList);
  }
}
