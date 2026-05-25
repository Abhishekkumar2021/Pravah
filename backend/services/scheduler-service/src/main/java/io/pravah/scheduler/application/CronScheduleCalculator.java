package io.pravah.scheduler.application;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Parses cron expressions and computes next run times (US-03.01). */
public final class CronScheduleCalculator {

  private static final CronParser UNIX_PARSER =
      new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
  private static final CronParser QUARTZ_PARSER =
      new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));

  private CronScheduleCalculator() {}

  public static Cron parse(String expression) {
    String trimmed = expression.trim();
    int fields = trimmed.split("\\s+").length;
    if (fields == 5) {
      return UNIX_PARSER.parse(trimmed);
    }
    if (fields == 6 || fields == 7) {
      return QUARTZ_PARSER.parse(trimmed);
    }
    throw new IllegalArgumentException(
        "Cron expression must have 5 fields (minute hour day month weekday) or 6–7 Quartz fields");
  }

  public static ZoneId zoneId(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid timezone: " + timezone, e);
    }
  }

  public static String describe(String expression, Locale locale) {
    Cron cron = parse(expression);
    CronDescriptor descriptor = CronDescriptor.instance(locale);
    return descriptor.describe(cron);
  }

  public static Instant nextRunAfter(String expression, String timezone, Instant after) {
    ExecutionTime executionTime = ExecutionTime.forCron(parse(expression));
    ZonedDateTime zonedAfter = after.atZone(zoneId(timezone));
    Optional<ZonedDateTime> next = executionTime.nextExecution(zonedAfter);
    return next.map(ZonedDateTime::toInstant)
        .orElseThrow(() -> new IllegalArgumentException("No next execution for cron expression"));
  }

  public static List<Instant> nextRuns(
      String expression, String timezone, Instant after, int count) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be >= 1");
    }
    ExecutionTime executionTime = ExecutionTime.forCron(parse(expression));
    ZonedDateTime cursor = after.atZone(zoneId(timezone));
    List<Instant> runs = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Optional<ZonedDateTime> next = executionTime.nextExecution(cursor);
      if (next.isEmpty()) {
        break;
      }
      ZonedDateTime run = next.get();
      runs.add(run.toInstant());
      cursor = run;
    }
    return runs;
  }
}
