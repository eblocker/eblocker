/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.UpdatingStatus;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.data.systemstatus.SubSystemDetails;
import org.eblocker.server.common.data.systemstatus.SubSystemStatus;
import org.eblocker.server.common.data.systemstatus.SystemStatusDetails;
import org.eblocker.server.common.update.SystemUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class SystemStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(SystemStatusService.class);

    private static final String MSG_KEY_PREFIX = "SUB_SYSTEM_STATUS_";
    private static final String MSG_KEY_SEPARATOR = "_";
    private final String projectVersion;

    private ExecutionState executionState = ExecutionState.BOOTING;

    private final List<ExecutionStateChangeListener> listeners = new ArrayList<>();

    private final List<Exception> warnings = new ArrayList<>();

    private final Map<SubSystem, SubSystemDetails> details = new EnumMap<>(SubSystem.class);

    private SystemUpdater systemUpdater;

    private UpdatingStatus updatingState;

    @Inject
    public SystemStatusService(@Named("project.version") String projectVersion) {
        this.projectVersion = projectVersion;
    }

    public void setSystemUpdater(SystemUpdater systemUpdater) {
        this.systemUpdater = systemUpdater;
    }

    public void setExecutionState(ExecutionState executionState) {
        this.executionState = executionState;
        notifyListeners(executionState);
    }

    public ExecutionState getExecutionState() {
        return executionState;
    }

    public SystemStatusDetails getSystemStatusDetails() {
        if (updatingState != null && updatingState.isUpdating() && systemUpdater != null) {
            // If current state is "UPDATING" and if we have an SystemUpdater,
            // try to find out if the update status has changed.
            try {
                updatingState = systemUpdater.getUpdatingStatus();
            } catch (IOException e) {
                LOG.error("Cannot retrieve update status at this time", e);
            } catch (InterruptedException e) {
                LOG.error("GetSystemStatusDetails interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
        return new SystemStatusDetails(
                new Date(),
                (updatingState == null || !updatingState.isUpdating() ? executionState : ExecutionState.UPDATING),
                warnings.stream().map(Throwable::getMessage).collect(Collectors.toList()),
                details.values(),
                projectVersion,
                updatingState
        );
    }

    public void addWarning(Exception e) {
        warnings.add(e);
    }

    public SystemStatusService starting(SubSystem subSystem) {
        return starting(subSystem, Collections.emptyMap());
    }

    public SystemStatusService starting(SubSystem subSystem, Map<String, Object> msgContext) {
        return updateSubSystem(subSystem, details(subSystem, SubSystemStatus.STARTING, msgContext));
    }

    public SystemStatusService ok(SubSystem subSystem) {
        return ok(subSystem, Collections.emptyMap());
    }

    public SystemStatusService ok(SubSystem subSystem, String key, Object value) {
        return ok(subSystem, Collections.singletonMap(key, value));
    }

    public SystemStatusService ok(SubSystem subSystem, Map<String, Object> msgContext) {
        return updateSubSystem(subSystem, details(subSystem, SubSystemStatus.OK, msgContext));
    }

    public SystemStatusService warn(SubSystem subSystem) {
        return warn(subSystem, Collections.emptyMap());
    }

    public SystemStatusService warn(SubSystem subSystem, Map<String, Object> msgContext) {
        return updateSubSystem(subSystem, details(subSystem, SubSystemStatus.WARN, msgContext));
    }

    public SystemStatusService error(SubSystem subSystem) {
        return error(subSystem, Collections.emptyMap());
    }

    public SystemStatusService error(SubSystem subSystem, String error) {
        return error(subSystem, Collections.singletonMap("error", error));
    }

    public SystemStatusService error(SubSystem subSystem, Map<String, Object> msgContext) {
        return updateSubSystem(subSystem, details(subSystem, SubSystemStatus.ERROR, msgContext));
    }

    public SystemStatusService off(SubSystem subSystem) {
        return off(subSystem, Collections.emptyMap());
    }

    public SystemStatusService off(SubSystem subSystem, Map<String, Object> msgContext) {
        return updateSubSystem(subSystem, details(subSystem, SubSystemStatus.OFF, msgContext));
    }

    private SystemStatusService updateSubSystem(SubSystem subSystem, SubSystemDetails subSystemDetails) {
        details.put(subSystem, subSystemDetails);
        return this;
    }

    private SubSystemDetails details(SubSystem subSystem, SubSystemStatus status, Map<String, Object> msgContext) {
        return new SubSystemDetails(
                subSystem.name(),
                status.name(),
                subSystem.getOrder(),
                MSG_KEY_PREFIX + subSystem.name() + MSG_KEY_SEPARATOR + status.name(),
                msgContext
        );
    }

    public List<Exception> getWarnings() {
        return warnings;
    }

    public void setUpdatingStatus(UpdatingStatus state) {
        this.updatingState = state;
    }

    // get notified of changes of ExecutionState:
    public interface ExecutionStateChangeListener {
        void onChange(ExecutionState newState);
    }

    public void addListener(ExecutionStateChangeListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(ExecutionState newState) {
        listeners.forEach(listener -> listener.onChange(newState));
    }
}
