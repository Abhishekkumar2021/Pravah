package io.pravah.tenant.infrastructure.email;

import io.pravah.tenant.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends email verification links via SMTP (Mailhog in local dev). */
@Service
public class EmailVerificationEmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationEmailService.class);

  private final JavaMailSender mailSender;
  private final AuthProperties authProperties;

  public EmailVerificationEmailService(JavaMailSender mailSender, AuthProperties authProperties) {
    this.mailSender = mailSender;
    this.authProperties = authProperties;
  }

  public void sendVerificationLink(String toEmail, String rawToken) {
    String verifyUrl =
        authProperties.frontendBaseUrl().replaceAll("/$", "") + "/verify-email?token=" + rawToken;
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(authProperties.mailFrom());
    message.setTo(toEmail);
    message.setSubject("Verify your Pravah account");
    message.setText(
        """
        Welcome to Pravah.

        Open this link to verify your email (valid for %s):
        %s

        If you did not create an account, you can ignore this email.
        """
            .formatted(authProperties.emailVerificationTokenTtl(), verifyUrl));

    try {
      mailSender.send(message);
      log.info("Verification email sent to {}", toEmail);
    } catch (MailException e) {
      log.error("Failed to send verification email to {}", toEmail, e);
      throw e;
    }
  }
}
