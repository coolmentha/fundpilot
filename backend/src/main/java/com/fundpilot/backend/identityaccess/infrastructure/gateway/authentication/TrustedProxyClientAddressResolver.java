package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.LoginClientAddressResolver;
import com.fundpilot.backend.identityaccess.infrastructure.configuration.LoginRateLimitProperties;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrustedProxyClientAddressResolver implements LoginClientAddressResolver {

    private final LoginRateLimitProperties properties;
    private volatile List<IpNetwork> trustedNetworks;

    @Override
    public String resolve(String remoteAddress, String forwardedFor) {
        String remote = normalizeAddress(remoteAddress);
        List<IpNetwork> networks = trustedNetworks();
        if (networks.isEmpty() || !isTrusted(remote, networks)) {
            return remote;
        }
        List<String> forwardedAddresses = parseForwardedAddresses(forwardedFor);
        String candidate = remote;
        for (int index = forwardedAddresses.size() - 1; index >= 0; index--) {
            if (!isTrusted(candidate, networks)) {
                return candidate;
            }
            candidate = forwardedAddresses.get(index);
        }
        return candidate;
    }

    private List<IpNetwork> trustedNetworks() {
        List<IpNetwork> cached = trustedNetworks;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (trustedNetworks == null) {
                trustedNetworks = properties.trustedProxies().stream()
                        .map(IpNetwork::parse)
                        .toList();
            }
            return trustedNetworks;
        }
    }

    private List<String> parseForwardedAddresses(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return List.of();
        }
        return Arrays.stream(forwardedFor.split(","))
                .map(this::normalizeAddressOrNull)
                .filter(address -> address != null)
                .toList();
    }

    private boolean isTrusted(String address, List<IpNetwork> networks) {
        return networks.stream().anyMatch(network -> network.contains(address));
    }

    private String normalizeAddress(String address) {
        String normalized = normalizeAddressOrNull(address);
        return normalized == null ? "unknown" : normalized;
    }

    private String normalizeAddressOrNull(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        byte[] parsed = parseAddress(normalized);
        if (parsed == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(parsed).getHostAddress();
        } catch (Exception exception) {
            return null;
        }
    }

    private static byte[] parseAddress(String address) {
        try {
            return InetAddress.ofLiteral(address).getAddress();
        } catch (Exception exception) {
            return null;
        }
    }

    private record IpNetwork(byte[] networkAddress, int prefixLength) {

        private static IpNetwork parse(String value) {
            String normalized = value.trim();
            int separator = normalized.indexOf('/');
            String addressPart = separator < 0 ? normalized : normalized.substring(0, separator);
            byte[] address = parseAddress(addressPart);
            if (address == null) {
                throw new IllegalArgumentException("可信代理地址无效: " + value);
            }
            int maxPrefix = address.length * 8;
            int prefix = separator < 0 ? maxPrefix : Integer.parseInt(normalized.substring(separator + 1));
            if (prefix < 0 || prefix > maxPrefix) {
                throw new IllegalArgumentException("可信代理网段无效: " + value);
            }
            byte[] network = address.clone();
            for (int index = 0; index < network.length; index++) {
                int remaining = prefix - index * 8;
                int mask = remaining >= 8 ? 0xff : remaining <= 0 ? 0 : 0xff << (8 - remaining);
                network[index] = (byte) (network[index] & mask);
            }
            return new IpNetwork(network, prefix);
        }

        private boolean contains(String address) {
            byte[] candidate = parseAddress(address);
            if (candidate == null || candidate.length != networkAddress.length) {
                return false;
            }
            for (int index = 0; index < candidate.length; index++) {
                int remaining = prefixLength - index * 8;
                int mask = remaining >= 8 ? 0xff : remaining <= 0 ? 0 : 0xff << (8 - remaining);
                if ((candidate[index] & mask) != (networkAddress[index] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
