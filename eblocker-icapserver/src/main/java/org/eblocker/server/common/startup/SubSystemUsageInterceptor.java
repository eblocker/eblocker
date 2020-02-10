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
package org.eblocker.server.common.startup;

import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.data.systemstatus.SubSystemStatus;
import org.eblocker.server.http.service.SystemStatusService;
import com.google.inject.Provider;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubSystemUsageInterceptor implements MethodInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SubSystemUsageInterceptor.class);

    private final Provider<SystemStatusService> statusServiceProvider;

    private boolean booted = false;

    public SubSystemUsageInterceptor(Provider<SystemStatusService> statusServiceProvider) {
        this.statusServiceProvider = statusServiceProvider;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (booted) {
            return invocation.proceed();
        }

        SystemStatusService statusService = statusServiceProvider.get();
        if (statusService.getExecutionState() != ExecutionState.BOOTING) {
            booted = true;
            return invocation.proceed();
        }

        SubSystemService subSystemService = findSubSystemService(invocation.getThis().getClass());
        if (subSystemService == null) {
            log.error("can not apply SubSystemUsageInterceptor on non-annotated class {}", invocation.getThis());
            throw new StartupContractViolation("can not apply SubSystemUsageInterceptor on non-annotated class " + invocation.getThis());
        }

        if (subSystemService.allowUninitializedCalls() || isRequiredSubSystemStarted(statusService, subSystemService.value())) {
            return invocation.proceed();
        } else {
            log.error("Startup contract violation {} / {} called to early!", invocation.getThis().getClass(), invocation.getMethod());
            throw new StartupContractViolation("Startup contract violation: " + invocation.getThis().getClass() + " / " + invocation.getMethod() + " called too early!");
        }
    }

    private boolean isRequiredSubSystemStarted(SystemStatusService statusService, SubSystem subSystem) {
        return !statusService.getSystemStatusDetails().getSubSystemDetails().stream()
            .filter(d -> d.getOrder() < subSystem.getOrder())
            .filter(d -> SubSystemStatus.valueOf(d.getStatus()) == SubSystemStatus.STARTING)
            .findAny()
            .isPresent();
    }

    private SubSystemService findSubSystemService(Class<?> clazz) {
        for(Class<?> i = clazz; i != null; i = i.getSuperclass()) {
            SubSystemService subSystemService = i.getAnnotation(SubSystemService.class);
            if (subSystemService != null) {
                return subSystemService;
            }
        }
        return null;
    }
}
