package org.eblocker.server.http.service;

import com.google.inject.Singleton;
import org.eblocker.server.common.network.NetworkUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Current LAN_ONLY route provider.
 *
 * WG-10A selected the already established WireGuard firewall contract because
 * no canonical product-level resolver for the actual local IPv4 LAN subnet
 * set exists yet. The values are referenced from NetworkUtils instead of
 * being duplicated here.
 */
@Singleton
public class WireGuardFirewallContractLanRouteProvider
        implements WireGuardLanRouteProvider {

    private static final List<String> ROUTES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            NetworkUtils.privateClassC,
                            NetworkUtils.privateClassB,
                            NetworkUtils.privateClassA,
                            NetworkUtils.linkLocal
                    )
            );

    @Override
    public List<String> getRoutes() {
        return ROUTES;
    }
}
