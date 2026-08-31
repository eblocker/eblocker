package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class WireGuardServerService {

    private static final Logger LOG =
            LoggerFactory.getLogger(WireGuardServerService.class);

    private final DataSource dataSource;
    private final WireGuardPeerService peerService;
    private final WireGuardServerControlService controlService;
    private final NetworkStateMachine networkStateMachine;
    private final ScheduledExecutorService executorService;

    @Inject
    public WireGuardServerService(
            DataSource dataSource,
            WireGuardPeerService peerService,
            WireGuardServerControlService controlService,
            NetworkStateMachine networkStateMachine,
            @Named("lowPrioScheduledExecutor")
                    ScheduledExecutorService executorService) {

        this.dataSource = dataSource;
        this.peerService = peerService;
        this.controlService = controlService;
        this.networkStateMachine = networkStateMachine;
        this.executorService = executorService;
    }

    @SubSystemInit
    public void init() {
        if (!isEnabled()) {
            return;
        }

        executorService.execute(this::restoreEnabledServer);
    }

    public synchronized void enable() {
        peerService.reconcilePeers();
        controlService.start();

        try {
            dataSource.setWireGuardServerState(true);

        } catch (RuntimeException persistenceException) {
            try {
                controlService.stop();

            } catch (RuntimeException rollbackException) {
                persistenceException.addSuppressed(
                        rollbackException
                );
            }

            throw new IllegalStateException(
                    "Could not persist enabled WireGuard state; "
                            + "runtime rollback was attempted.",
                    persistenceException
            );
        }

        networkStateMachine.updateFirewall();
    }

    public synchronized void disable() {
        controlService.stop();

        try {
            dataSource.setWireGuardServerState(false);

        } catch (RuntimeException persistenceException) {
            rollbackFailedDisable(persistenceException);

            throw new IllegalStateException(
                    "Could not persist disabled WireGuard state; "
                            + "runtime rollback was attempted.",
                    persistenceException
            );
        }

        networkStateMachine.updateFirewall();
    }

    public boolean isEnabled() {
        return dataSource.getWireGuardServerState();
    }

    private void restoreEnabledServer() {
        try {
            peerService.reconcilePeers();
            controlService.start();

        } catch (RuntimeException e) {
            LOG.error(
                    "Could not restore enabled WireGuard server "
                            + "during eBlocker startup.",
                    e
            );
        }
    }

    private void rollbackFailedDisable(
            RuntimeException originalException) {

        try {
            peerService.reconcilePeers();
            controlService.start();

        } catch (RuntimeException rollbackException) {
            originalException.addSuppressed(
                    rollbackException
            );
        }
    }
}
