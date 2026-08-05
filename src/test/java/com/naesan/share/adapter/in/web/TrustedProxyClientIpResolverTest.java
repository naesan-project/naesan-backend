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
                TrustedProxyClientIpResolver.FORWARDED_FOR_HEADER,
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
                TrustedProxyClientIpResolver.FORWARDED_FOR_HEADER,
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
                TrustedProxyClientIpResolver.FORWARDED_FOR_HEADER,
                "not-an-ip"
        );

        assertThat(resolver.resolve(request)).isEqualTo("172.20.0.5");
    }

    @Test
    @DisplayName("플랫폼과 Nginx를 거친 요청에서 최초 신뢰하지 않은 client IP를 사용한다")
    void resolvesClientAcrossMultipleTrustedProxies() {
        TrustedProxyClientIpResolver resolver =
                new TrustedProxyClientIpResolver(
                        "172.16.0.0/12,10.0.0.0/8"
                );
        MockHttpServletRequest request = requestFrom("172.20.0.5");
        request.addHeader(
                TrustedProxyClientIpResolver.FORWARDED_FOR_HEADER,
                "198.51.100.10, 10.20.0.7"
        );

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    @DisplayName("client가 추가한 앞쪽 IP보다 프록시가 덧붙인 실제 접속 주소를 우선한다")
    void ignoresSpoofedLeftmostForwardedAddress() {
        TrustedProxyClientIpResolver resolver =
                new TrustedProxyClientIpResolver("172.16.0.0/12");
        MockHttpServletRequest request = requestFrom("172.20.0.5");
        request.addHeader(
                TrustedProxyClientIpResolver.FORWARDED_FOR_HEADER,
                "203.0.113.99, 198.51.100.10"
        );

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
