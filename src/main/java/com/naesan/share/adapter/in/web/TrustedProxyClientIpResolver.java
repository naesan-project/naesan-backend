package com.naesan.share.adapter.in.web;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

public final class TrustedProxyClientIpResolver {
    static final String REAL_IP_HEADER = "X-Real-IP";
    private static final IpAddressMatcher IPV4_ADDRESS =
            new IpAddressMatcher("0.0.0.0/0");
    private static final IpAddressMatcher IPV6_ADDRESS =
            new IpAddressMatcher("::/0");

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public TrustedProxyClientIpResolver(String trustedProxyCidrs) {
        this.trustedProxyMatchers = Arrays.stream(trustedProxyCidrs.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String clientAddress = request.getHeader(REAL_IP_HEADER);
        if (clientAddress == null || !isIpAddress(clientAddress.strip())) {
            return remoteAddress;
        }
        return clientAddress.strip();
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return trustedProxyMatchers.stream()
                .anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private boolean isIpAddress(String address) {
        try {
            return IPV4_ADDRESS.matches(address) || IPV6_ADDRESS.matches(address);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
