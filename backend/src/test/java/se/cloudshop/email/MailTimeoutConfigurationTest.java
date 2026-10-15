package se.cloudshop.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailTimeoutConfigurationTest {

  @Test
  void configuredSmtpSenderHasFiniteConnectionReadAndWriteTimeouts() throws Exception {
    var properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    var source = new PropertiesPropertySource("application", properties);

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
        .withInitializer(context -> context.getEnvironment().getPropertySources().addLast(source))
        .withPropertyValues("spring.mail.host=smtp.example.invalid")
        .run(context -> {
          assertThat(context).hasNotFailed();
          var sender = context.getBean(JavaMailSenderImpl.class);
          assertThat(sender.getJavaMailProperties())
              .containsEntry("mail.smtp.starttls.required", "true")
              .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
              .containsEntry("mail.smtp.connectiontimeout", "5000")
              .containsEntry("mail.smtp.timeout", "10000")
              .containsEntry("mail.smtp.writetimeout", "10000");
        });
  }
}
