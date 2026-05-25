package io.pravah.execution.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ExecutionRealtimeAfterCommitPublisher {

  private final ExecutionRealtimeFanout fanout;

  public ExecutionRealtimeAfterCommitPublisher(ExecutionRealtimeFanout fanout) {
    this.fanout = fanout;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onCommitted(ExecutionRealtimeNotificationEvent event) {
    fanout.publish(event.tenantId(), event.jsonPayload());
  }
}
