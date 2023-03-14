package com.smilebat.learntribe.reactor.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Overrided Configuration for HTTPS in tomcat
 *
 * <p>Key store generation for executable jars.
 *
 * <p>Copyright &copy; 2023 Smile .Bat
 *
 * @author Pai,Sai Nandan
 */
@Configuration
@SuppressFBWarnings(justification = "PATH_TRAVERSAL_IN")
@Slf4j
public class TomcatConfig {

  /**
   * Servlet container.
   *
   * @return the {@link TomcatServletWebServerFactory}
   * @throws IOException on exception.
   */
  @Bean
  public TomcatServletWebServerFactory servletContainer() throws IOException {
    final int port = 8443;
    try {
      InputStream keystoreStream = getClass().getResourceAsStream("/security.p12");
      String keystoreFile = new File(".").getCanonicalPath() + File.separator + "security.p12";
      Files.copy(keystoreStream, Paths.get(keystoreFile), StandardCopyOption.REPLACE_EXISTING);
      final String keystorePass = "changeit";
      final String keystoreType = "pkcs12";
      final String keystoreProvider = "SunJSSE";
      final String keystoreAlias = "smilebat";

      TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(port);
      factory.addConnectorCustomizers(
          con -> {
            Http11NioProtocol proto = (Http11NioProtocol) con.getProtocolHandler();
            proto.setSSLEnabled(true);
            con.setScheme("https");
            con.setSecure(true);
            proto.setKeystoreFile(keystoreFile);
            proto.setKeystorePass(keystorePass);
            proto.setKeystoreType(keystoreType);
            proto.setProperty("keystoreProvider", keystoreProvider);
            proto.setKeyAlias(keystoreAlias);
          });
      return factory;
    } catch (Exception e) {
      log.error(e.getLocalizedMessage());
    }
    return null;
  }
}
