package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class WireGuardServerService {

    private static final Logger log = LoggerFactory.getLogger(WireGuardServerService.class);

    private final ScriptRunner scriptRunner;
    private final String wireGuardServerCommand;

    @Inject
    public WireGuardServerService(ScriptRunner scriptRunner,
                                  @Named("wireguard.server.command") String wireGuardServerCommand) {
        this.scriptRunner = scriptRunner;
        this.wireGuardServerCommand = wireGuardServerCommand;
    }

    public boolean start() {
        return run("start");
    }

    public boolean stop() {
        return run("stop");
    }

    public boolean init() {
        return run("init");
    }

    private boolean run(String mode) {
        try {
            return scriptRunner.runScript(wireGuardServerCommand, mode) == 0;
        } catch (IOException e) {
            log.error("Could not {} WireGuard server", mode, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WireGuard server control interrupted ({})", mode, e);
        }
        return false;
    }
}
