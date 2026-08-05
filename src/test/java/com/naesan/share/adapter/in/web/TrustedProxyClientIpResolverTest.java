package com.naesan.share.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedProxyClientIpResolverTest {

    @Test
    @DisplayName("신뢰한 프록시의 유효한 실제 client IP를 사용한다")
    void resolvesClientAddressFromTrustedProxy() {
        TrustedProxyClientIpResolver resolver =
                new TrustedProxyClientIpResolver("172.16.0.0/12");
        MockHttpServletRequest request = requestFrom("172.20.0.5");
        request.addHeader(
                TrustedProxyClientIpResolver.REAL_IP_HEADER,
                "198.51.100.10"
        );

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    @DisplayName("신뢰하지 않은 요청의 실제 IP 헤더는 무시한다")
    void ignoresSpoofedAddressFromUntrustedSource() {
        TrustedProxyClientIpResolver resolver =
                new TrustedProxyClientIpResolver("172.16.0.0/12");
        MockHttpServletRequest request = requestFrom("203.0.113.10");
        request.addHeader(
                TrustedProxyClientIpResolver.REAL_IP_HEADER,
                "198.51.100.10"
        );

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("신뢰한 프록시가 잘못된 IP를 전달하면 프록시 주소를 사용한다")
    void fallsBackWhenForwardedAddressIsInvalid() {
        TrustedProxyClientIpResolver resolver =
                new TrustedProxyClientIpResolver("172.16.0.0/12");
        MockHttpServletRequest request = requestFrom("172.20.0.5");
        request.addHeader(
                TrustedProxyClientIpResolver.REAL_IP_HEADER,
                "not-an-ip"
        );

        assertThat(resolver.resolve(request)).isEqualTo("172.20.0.5");
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
