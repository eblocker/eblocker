package org.eblocker.server.http.service;

import com.google.inject.ImplementedBy;

import java.util.List;

/**
 * Resolves the route contract used by LAN_ONLY peers.
 *
 * Keeping this behind an interface is intentional: a future release can add
 * a canonical local-subnet resolver without changing persisted peer intent,
 * the client-config renderer, or the public routing API.
 */
@ImplementedBy(WireGuardFirewallContractLanRouteProvider.class)
public interface WireGuardLanRouteProvider {

    List<String> getRoutes();
}
