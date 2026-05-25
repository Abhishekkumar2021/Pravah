package io.pravah.notification.infrastructure.channel;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.notification.application.dto.AlertContext;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Email notification channel using Spring Mail + Thymeleaf templates. */
@Component
public class EmailChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;

  public EmailChannel(JavaMailSender mailSender, TemplateEngine templateEngine) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
  }

  @Override
  public String type() {
    return "email";
  }

  @Override
  @SuppressWarnings("unchecked")
  public DeliveryResult send(AlertContext context, Map<String, Object> channelConfig) {
    List<String> recipients = (List<String>) channelConfig.get("recipients");
    if (recipients == null || recipients.isEmpty()) {
      return DeliveryResult.failure("email", "No recipients configured");
    }

    String from = (String) channelConfig.getOrDefault("from", "alerts@pravah.io");
    String subject = context.shortSummary();

    try {
      String htmlContent = renderTemplate(context);

      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(recipients.toArray(new String[0]));
      helper.setSubject(subject);
      helper.setText(htmlContent, true);

      mailSender.send(message);

      log.info(
          "Email alert sent",
          kv("event_type", context.eventType()),
          kv("pipeline_id", context.pipelineId()),
          kv("recipients", recipients.size()));

      return DeliveryResult.success("email", "Sent to " + recipients.size() + " recipient(s)");

    } catch (MessagingException e) {
      log.error(
          "Failed to send email alert",
          kv("event_type", context.eventType()),
          kv("pipeline_id", context.pipelineId()),
          e);
      return DeliveryResult.failure("email", e.getMessage());
    }
  }

  private String renderTemplate(AlertContext context) {
    Context thymeleafContext = new Context();
    thymeleafContext.setVariable("context", context);
    thymeleafContext.setVariable("title", context.shortSummary());
    thymeleafContext.setVariable("eventType", context.eventType());
    thymeleafContext.setVariable("pipelineName", context.pipelineName());
    thymeleafContext.setVariable("status", context.status());
    thymeleafContext.setVariable("errorMessage", context.errorMessage());
    thymeleafContext.setVariable("stageName", context.stageName());
    thymeleafContext.setVariable("occurredAt", context.occurredAt());
    thymeleafContext.setVariable("executionUrl", context.executionUrl());
    thymeleafContext.setVariable("pipelineUrl", context.pipelineUrl());

    return templateEngine.process("alert-email", thymeleafContext);
  }
}
