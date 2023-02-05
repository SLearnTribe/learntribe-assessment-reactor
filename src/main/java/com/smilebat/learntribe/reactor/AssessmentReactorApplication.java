package com.smilebat.learntribe.reactor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * A Generic request mock
 *
 * <p>Copyright &copy; 2022 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(
    basePackages = {"com.smilebat.learntribe.learntribeclients", "com.smilebat.learntribe"})
@EnableFeignClients(basePackages = "com.smilebat.learntribe.learntribeclients")
@EnableJpaRepositories(
    basePackages = {"com.smilebat.learntribe.dataaccess", "com.smilebat.learntribe.dataaccess.jpa"})
@EntityScan("com.smilebat.learntribe.dataaccess.jpa")
@EnableSwagger2
@EnableJpaAuditing
public class AssessmentReactorApplication {

  /**
   * Main method
   *
   * @param args the args
   */
  public static void main(String[] args) {
    SpringApplication.run(AssessmentReactorApplication.class, args);
  }
}
