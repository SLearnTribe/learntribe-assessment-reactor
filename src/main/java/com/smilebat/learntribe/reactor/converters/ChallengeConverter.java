package com.smilebat.learntribe.reactor.converters;

import com.smilebat.learntribe.assessment.response.ChallengeResponse;
import com.smilebat.learntribe.dataaccess.jpa.entity.Challenge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Challenge Converter to map between Entities , Request and Response
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 */
@Component
public final class ChallengeConverter {

  /**
   * Converts the {@link Challenge} to {@link ChallengeResponse}.
   *
   * @param challenge the {@link Challenge}
   * @return the {@link ChallengeResponse}
   */
  public ChallengeResponse toResponse(Challenge challenge) {
    ChallengeResponse challengeResponse = new ChallengeResponse();
    challengeResponse.setId(challenge.getId());
    challengeResponse.setQuestion(challenge.getQuestion());
    String[] options = challenge.getOptions();
    List<String> responseOptions = new ArrayList<>(options.length);
    for (String option : options) {
      if (option != null && !option.isEmpty()) {
        responseOptions.add(option);
      }
    }
    challengeResponse.setOptions(responseOptions);
    return challengeResponse;
  }

  /**
   * Converts the List of {@link Challenge} to List of {@link ChallengeResponse}.
   *
   * @param challenges the {@link Challenge}
   * @return the {@link ChallengeResponse}
   */
  public List<ChallengeResponse> toResponse(Collection<Challenge> challenges) {
    return challenges.stream().map(this::toResponse).collect(Collectors.toList());
  }
}
