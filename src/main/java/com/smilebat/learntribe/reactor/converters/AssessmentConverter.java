package com.smilebat.learntribe.reactor.converters;

import com.smilebat.learntribe.assessment.response.AssessmentResponse;
import com.smilebat.learntribe.dataaccess.jpa.entity.Assessment;
import com.smilebat.learntribe.enums.AssessmentDifficulty;
import com.smilebat.learntribe.enums.AssessmentType;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assessment Converter to map between Entities , Request and Response
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Component
@RequiredArgsConstructor
public final class AssessmentConverter {

  /**
   * Converts the {@link Assessment} to {@link AssessmentResponse}.
   *
   * @param assessment the {@link Assessment}
   * @return the {@link AssessmentResponse}
   */
  public AssessmentResponse toResponse(Assessment assessment) {
    AssessmentResponse response = new AssessmentResponse();
    response.setId(assessment.getId());
    response.setTitle(assessment.getTitle());
    response.setProgress(assessment.getProgress());
    response.setNumOfQuestions(assessment.getQuestions());
    response.setDescription(assessment.getDescription());
    if (assessment.getStatus() != null) {
      response.setStatus(assessment.getStatus().name());
    }

    AssessmentDifficulty difficulty = assessment.getDifficulty();
    if (difficulty != null) {
      response.setDifficulty(difficulty.name());
    }

    AssessmentType assessmentType = assessment.getType();
    if (assessmentType != null) {
      response.setType(assessmentType.toString());
    }

    if (assessment.getRelatedJobId() != null) {
      response.setRelatedJobId(assessment.getRelatedJobId());
    }
    return response;
  }

  /**
   * Converts the List of {@link Assessment} to List of {@link AssessmentResponse}.
   *
   * @param assessmentList the List of {@link Assessment}
   * @return the List of {@link AssessmentResponse}
   */
  public List<AssessmentResponse> toResponse(Collection<Assessment> assessmentList) {
    return assessmentList.stream().map(this::toResponse).collect(Collectors.toList());
  }
}
