package io.pravah.pipeline.api.internal;

import io.pravah.pipeline.application.ConnectionApplicationService;
import io.pravah.pipeline.application.ResolvedConnection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal API for resolving named connections during stage execution. */
@RestController
@RequestMapping("/api/v1/internal/connections")
public class InternalConnectionController {

  private final ConnectionApplicationService connectionApplicationService;

  public InternalConnectionController(ConnectionApplicationService connectionApplicationService) {
    this.connectionApplicationService = connectionApplicationService;
  }

  @GetMapping("/{name}")
  public ResolvedConnection resolve(@PathVariable String name) {
    return connectionApplicationService.resolveForExecution(name);
  }
}
