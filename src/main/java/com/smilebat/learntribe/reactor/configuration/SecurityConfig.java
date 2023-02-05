package com.smilebat.learntribe.reactor.configuration;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(
    prePostEnabled = true, // Enables @PreAuthorize and @PostAuthorize
    securedEnabled = true, // Enables @Secured
    jsr250Enabled = true // Enables @RolesAllowed (Ensures JSR-250 annotations are enabled)
    )
@Slf4j
@SuppressFBWarnings(justification = "Generated code")
public class SecurityConfig {

  @Value("${feign.client.url.keycloak}")
  private String keyCloakBaseUrl;

  @Bean
  protected SecurityFilterChain configure(HttpSecurity security) throws Exception {

    JwtIssuerAuthenticationManagerResolver authenticationManagerResolver =
        new JwtIssuerAuthenticationManagerResolver(keyCloakBaseUrl + "/realms/master");

    security
        .cors()
        .and()
        .csrf()
        .disable()
        .authorizeRequests(
            authorize ->
                authorize
                    .antMatchers(
                        "/swagger-ui.html",
                        "/v2/api-docs",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/configuration/ui",
                        "/configuration/security",
                        "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);
        .oauth2ResourceServer(
            oauth2 -> oauth2.authenticationManagerResolver(authenticationManagerResolver));
    return security.build();
  }

  /**
   * Custom cors configuration.
   *
   * @return the {@link CorsConfigurationSource}
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    final CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(ImmutableList.of("*")); // www - obligatory
    // configuration.setAllowedOrigins(ImmutableList.of("*"));  //set access from all domains
    configuration.setAllowedMethods(ImmutableList.of("GET", "POST", "PUT", "DELETE"));
    // configuration.setAllowCredentials(true);
    // configuration.setAllowedHeaders(ImmutableList.of("Authorization", "Cache-Control",
    // "Content-Type"));
    configuration.setAllowedHeaders(ImmutableList.of("*"));

    final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }
}
