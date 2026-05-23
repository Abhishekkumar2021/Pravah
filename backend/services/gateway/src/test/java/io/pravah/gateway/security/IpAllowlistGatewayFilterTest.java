package io.pravah.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpAllowlistGatewayFilterTest {

  @Test
  void matchesIpv4Cidr() {
    IpAllowlistGatewayFilter filter =
        new IpAllowlistGatewayFilter(true, java.util.List.of("10.0.0.0/8"));
    assertThat(invokeIsAllowed(filter, "10.1.2.3")).isTrue();
    assertThat(invokeIsAllowed(filter, "192.168.1.1")).isFalse();
  }

  @Test
  void matchesExactIp() {
    IpAllowlistGatewayFilter filter =
        new IpAllowlistGatewayFilter(true, java.util.List.of("127.0.0.1"));
    assertThat(invokeIsAllowed(filter, "127.0.0.1")).isTrue();
    assertThat(invokeIsAllowed(filter, "127.0.0.2")).isFalse();
  }

  private static boolean invokeIsAllowed(IpAllowlistGatewayFilter filter, String ip) {
    try {
      var method = IpAllowlistGatewayFilter.class.getDeclaredMethod("isAllowed", String.class);
      method.setAccessible(true);
      return (boolean) method.invoke(filter, ip);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
