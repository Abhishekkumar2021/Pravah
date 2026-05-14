package io.pravah.spring.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RlsAspectTest {

  private static final String SET_TENANT_SQL =
      "SELECT set_config('pravah.current_tenant_id', cast(? as text), true)";

  @Mock private EntityManager entityManager;
  @Mock private Session session;

  @InjectMocks private RlsAspect rlsAspect;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    lenient().when(entityManager.unwrap(Session.class)).thenReturn(session);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void setTenantContextBeforeTransaction_withTenantId_setsRlsVariable() throws Exception {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    Connection connection = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(connection.prepareStatement(SET_TENANT_SQL)).thenReturn(ps);

    captureAndRunWork(connection);

    rlsAspect.setTenantContextBeforeTransaction();

    verify(connection).prepareStatement(SET_TENANT_SQL);
    verify(ps).setString(1, tenantId.toString());
    verify(ps).execute();
    verify(ps).close();
  }

  @Test
  void setTenantContextBeforeTransaction_withoutTenantId_doesNotExecuteQuery() {
    rlsAspect.setTenantContextBeforeTransaction();

    verify(session, never()).doWork(any(Work.class));
  }

  @Test
  void setTenantContextBeforeTransaction_afterContextCleared_doesNotExecuteQuery() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.clear();

    rlsAspect.setTenantContextBeforeTransaction();

    verify(session, never()).doWork(any(Work.class));
  }

  @Test
  void setTenantContextBeforeTransaction_multipleCallsSameTenant_executesEachTime()
      throws Exception {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    Connection connection = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(connection.prepareStatement(SET_TENANT_SQL)).thenReturn(ps);

    captureAndRunWork(connection);

    rlsAspect.setTenantContextBeforeTransaction();
    rlsAspect.setTenantContextBeforeTransaction();

    verify(session, times(2)).doWork(any(Work.class));
    verify(ps, times(2)).setString(1, tenantId.toString());
    verify(ps, times(2)).execute();
    verify(ps, times(2)).close();
  }

  @Test
  void setTenantContextBeforeTransaction_differentTenants_setsCorrectIds() throws Exception {
    UUID tenantId1 = UUID.randomUUID();
    UUID tenantId2 = UUID.randomUUID();

    Connection connection = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(connection.prepareStatement(SET_TENANT_SQL)).thenReturn(ps);

    doAnswer(
            invocation -> {
              invocation.<Work>getArgument(0).execute(connection);
              return null;
            })
        .when(session)
        .doWork(any(Work.class));

    TenantContext.setCurrentTenantId(tenantId1);
    rlsAspect.setTenantContextBeforeTransaction();
    TenantContext.setCurrentTenantId(tenantId2);
    rlsAspect.setTenantContextBeforeTransaction();

    var inOrder = inOrder(ps);
    inOrder.verify(ps).setString(1, tenantId1.toString());
    inOrder.verify(ps).execute();
    inOrder.verify(ps).close();
    inOrder.verify(ps).setString(1, tenantId2.toString());
    inOrder.verify(ps).execute();
    inOrder.verify(ps).close();
  }

  @Test
  void setTenantContext_sqlUsesSetConfigForParameterizedTenant() {
    assertThat(SET_TENANT_SQL)
        .contains("set_config")
        .contains("pravah.current_tenant_id")
        .contains("true");
  }

  private void captureAndRunWork(Connection connection) {
    doAnswer(
            invocation -> {
              invocation.<Work>getArgument(0).execute(connection);
              return null;
            })
        .when(session)
        .doWork(any(Work.class));
  }
}
