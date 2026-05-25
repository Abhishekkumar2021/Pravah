package io.pravah.tenant.infrastructure.email;

import io.pravah.tenant.infrastructure.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends password reset links via SMTP (Mailhog in local dev). */
@Service
public class PasswordResetEmailService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailService.class);

  private final JavaMailSender mailSender;
  private final AuthProperties authProperties;

  public PasswordResetEmailService(JavaMailSender mailSender, AuthProperties authProperties) {
    this.mailSender = mailSender;
    this.authProperties = authProperties;
  }

  public void sendResetLink(String toEmail, String rawToken) {
    String resetUrl =
        authProperties.frontendBaseUrl().replaceAll("/$", "") + "/reset-password?token=" + rawToken;
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(authProperties.mailFrom());
    message.setTo(toEmail);
    message.setSubject("Reset your Pravah password");
    message.setText(
        """
        You requested a password reset for your Pravah account.

        Open this link to choose a new password (valid for %s):
        %s

        If you did not request this, you can ignore this email.
        """
            .formatted(authProperties.passwordResetTokenTtl(), resetUrl));

    try {
      mailSender.send(message);
      log.info("Password reset email sent to {}", toEmail);
    } catch (MailException e) {
      log.error("Failed to send password reset email to {}", toEmail, e);
      throw e;
    }
  }
}
