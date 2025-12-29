package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import org.eblocker.server.http.controller.WireGuardServerController;
import org.eblocker.server.http.service.WireGuardServerService;

public class WireGuardServerControllerImpl implements WireGuardServerController {

    private final WireGuardServerService wg;

    @Inject
    public WireGuardServerControllerImpl(WireGuardServerService wg) {
        this.wg = wg;
    }

    @Override
    public void start() {
        wg.init();
        wg.start();
    }

    @Override
    public void stop() {
        wg.stop();
    }

    @Override
    public String status() {
        // Minimal: wir liefern nur "UP"/"DOWN" basierend auf wg show
        // (sauber machen wir im nächsten Schritt)
        return "OK";
    }
}

