package io.pravah.pipeline.api;

import io.pravah.pipeline.api.dto.ConnectionResponse;
import io.pravah.pipeline.api.dto.CreateConnectionRequest;
import io.pravah.pipeline.api.dto.TestConnectionResponse;
import io.pravah.pipeline.api.dto.UpdateConnectionRequest;
import io.pravah.pipeline.application.ConnectionApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {

  private final ConnectionApplicationService connectionApplicationService;

  public ConnectionController(ConnectionApplicationService connectionApplicationService) {
    this.connectionApplicationService = connectionApplicationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ConnectionResponse create(@Valid @RequestBody CreateConnectionRequest request) {
    return connectionApplicationService.createConnection(request);
  }

  @GetMapping
  public List<ConnectionResponse> list() {
    return connectionApplicationService.listConnections();
  }

  @GetMapping("/{id}")
  public ConnectionResponse get(@PathVariable UUID id) {
    return connectionApplicationService.getConnection(id);
  }

  @PutMapping("/{id}")
  public ConnectionResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateConnectionRequest request) {
    return connectionApplicationService.updateConnection(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    connectionApplicationService.deleteConnection(id);
  }

  @PostMapping("/{id}/test")
  public TestConnectionResponse test(@PathVariable UUID id) {
    return connectionApplicationService.testConnection(id);
  }
}
