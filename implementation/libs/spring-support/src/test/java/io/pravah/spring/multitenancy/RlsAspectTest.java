package io.pravah.spring.multitenancy;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RlsAspectTest {

  @Mock private EntityManager entityManager;
  @Mock private Query nativeQuery;

  @InjectMocks private RlsAspect rlsAspect;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void setTenantContextBeforeTransaction_withTenantId_setsRlsVariable() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), anyString())).thenReturn(nativeQuery);

    rlsAspect.setTenantContextBeforeTransaction();

    verify(entityManager).createNativeQuery("SET LOCAL pravah.current_tenant_id = :tenantId");
    verify(nativeQuery).setParameter("tenantId", tenantId.toString());
    verify(nativeQuery).executeUpdate();
  }

  @Test
  void setTenantContextBeforeTransaction_withoutTenantId_doesNotExecuteQuery() {
    rlsAspect.setTenantContextBeforeTransaction();

    verify(entityManager, never()).createNativeQuery(anyString());
  }

  @Test
  void setTenantContextBeforeTransaction_afterContextCleared_doesNotExecuteQuery() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.clear();

    rlsAspect.setTenantContextBeforeTransaction();

    verify(entityManager, never()).createNativeQuery(anyString());
  }

  @Test
  void setTenantContextBeforeTransaction_multipleCallsSameTenant_executesEachTime() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), anyString())).thenReturn(nativeQuery);

    rlsAspect.setTenantContextBeforeTransaction();
    rlsAspect.setTenantContextBeforeTransaction();

    verify(entityManager, Mockito.times(2))
        .createNativeQuery("SET LOCAL pravah.current_tenant_id = :tenantId");
    verify(nativeQuery, Mockito.times(2)).setParameter(eq("tenantId"), eq(tenantId.toString()));
  }

  @Test
  void setTenantContextBeforeTransaction_differentTenants_setsCorrectIds() {
    UUID tenantId1 = UUID.randomUUID();
    UUID tenantId2 = UUID.randomUUID();

    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), anyString())).thenReturn(nativeQuery);

    TenantContext.setCurrentTenantId(tenantId1);
    rlsAspect.setTenantContextBeforeTransaction();
    verify(nativeQuery).setParameter("tenantId", tenantId1.toString());

    TenantContext.setCurrentTenantId(tenantId2);
    rlsAspect.setTenantContextBeforeTransaction();
    verify(nativeQuery).setParameter("tenantId", tenantId2.toString());
  }
}
