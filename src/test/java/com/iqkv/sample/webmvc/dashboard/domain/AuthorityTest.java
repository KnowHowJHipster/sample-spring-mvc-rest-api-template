package com.iqkv.sample.webmvc.dashboard.domain;

import static com.iqkv.sample.webmvc.dashboard.domain.AuthorityTestSamples.getAuthoritySample1;
import static com.iqkv.sample.webmvc.dashboard.domain.AuthorityTestSamples.getAuthoritySample2;
import static org.assertj.core.api.Assertions.assertThat;

import com.iqkv.sample.webmvc.dashboard.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AuthorityTest {

  @Test
  void equalsVerifier() throws Exception {
    TestUtil.equalsVerifier(Authority.class);
    Authority authority1 = getAuthoritySample1();
    Authority authority2 = new Authority();
    assertThat(authority1).isNotEqualTo(authority2);

    authority2.setName(authority1.getName());
    assertThat(authority1).isEqualTo(authority2);

    authority2 = getAuthoritySample2();
    assertThat(authority1).isNotEqualTo(authority2);
  }

  @Test
  void hashCodeVerifier() {
    Authority authority = new Authority();
    assertThat(authority.hashCode()).isZero();

    Authority authority1 = getAuthoritySample1();
    authority.setName(authority1.getName());
    assertThat(authority).hasSameHashCodeAs(authority1);
  }
}
