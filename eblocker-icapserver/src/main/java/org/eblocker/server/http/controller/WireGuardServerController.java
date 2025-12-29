package org.eblocker.server.http.controller;

public interface WireGuardServerController {
    void start();
    void stop();
    String status();
}
