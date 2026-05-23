package io.pravah.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpCidrMatcherTest {

  @Test
  void matchesIpv4Cidr() {
    assertThat(IpCidrMatcher.matches("10.1.2.3", "10.0.0.0/8")).isTrue();
    assertThat(IpCidrMatcher.matches("192.168.1.1", "10.0.0.0/8")).isFalse();
  }

  @Test
  void matchesExactIp() {
    assertThat(IpCidrMatcher.matches("127.0.0.1", "127.0.0.1")).isTrue();
  }

  @Test
  void matchesIpv6LoopbackCidr() {
    assertThat(IpCidrMatcher.matches("::1", "::1/128")).isTrue();
    assertThat(IpCidrMatcher.matches("::1", "127.0.0.1")).isFalse();
  }
}
