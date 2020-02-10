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
import org.eblocker.server.common.data.systemstatus.SubSystemDetails;
import org.eblocker.server.common.data.systemstatus.SubSystemStatus;
import org.eblocker.server.common.data.systemstatus.SystemStatusDetails;
import org.eblocker.server.http.service.SystemStatusService;
import com.google.inject.Provider;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Arrays;

public class SubSystemUsageInterceptorTest {

    private SystemStatusDetails statusDetails;
    private SystemStatusService statusService;
    private Provider<SystemStatusService> statusServiceProvider;
    private SubSystemUsageInterceptor interceptor;

    private Method notEnforcedSubSystemServiceMethod;
    private Method notEnforcedSubSystemServiceSuperClassMethod;
    private Method enforcedSubSystemServiceMethod;
    private Method enforcedSubSystemServiceSuperClassMethod;

    @Before
    public void setup() throws NoSuchMethodException {
        statusDetails = Mockito.mock(SystemStatusDetails.class);

        statusService = Mockito.mock(SystemStatusService.class);
        Mockito.when(statusService.getExecutionState()).thenReturn(ExecutionState.BOOTING);
        Mockito.when(statusService.getSystemStatusDetails()).thenReturn(statusDetails);

        statusServiceProvider = Mockito.mock(Provider.class);
        Mockito.when(statusServiceProvider.get()).thenReturn(statusService);

        notEnforcedSubSystemServiceMethod = NotEnforcedSubSystemService.class.getMethod("method");
        notEnforcedSubSystemServiceSuperClassMethod = NotEnforcedSubSystemService.class.getMethod("hashCode");
        enforcedSubSystemServiceMethod = EnforcedSubSystemService.class.getMethod("method");
        enforcedSubSystemServiceSuperClassMethod = EnforcedSubSystemService.class.getMethod("hashCode");

        interceptor = new SubSystemUsageInterceptor(() -> statusService);
    }

    @Test
    public void testSubSystemReady() throws Throwable {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Mockito.when(invocation.getThis()).thenReturn(new NotEnforcedSubSystemService());
        Mockito.when(invocation.getMethod()).thenReturn(notEnforcedSubSystemServiceMethod);
        Mockito.when(statusDetails.getSubSystemDetails()).thenReturn(Arrays.asList(new SubSystemDetails(SubSystem.HTTP_SERVER.name(), SubSystemStatus.OK.name(), SubSystem.HTTP_SERVER.getOrder(), null, null)));

        interceptor.invoke(invocation);
    }

    @Test
    public void testBooted() throws Throwable {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Mockito.when(invocation.getThis()).thenReturn(new NotEnforcedSubSystemService());
        Mockito.when(invocation.getMethod()).thenReturn(notEnforcedSubSystemServiceMethod);
        Mockito.when(statusDetails.getSubSystemDetails()).thenReturn(Arrays.asList(new SubSystemDetails(SubSystem.HTTP_SERVER.name(), SubSystemStatus.OK.name(), SubSystem.HTTP_SERVER.getOrder(), null, null)));

        Mockito.when(statusService.getExecutionState()).thenReturn(ExecutionState.RUNNING);
        interceptor.invoke(invocation);
        interceptor.invoke(invocation);

        Mockito.verify(statusService, Mockito.times(1)).getExecutionState();
    }

    @Test
    public void testSubSystemNotReady() throws Throwable {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Mockito.when(invocation.getThis()).thenReturn(new NotEnforcedSubSystemService());
        Mockito.when(invocation.getMethod()).thenReturn(notEnforcedSubSystemServiceMethod);
        Mockito.when(statusDetails.getSubSystemDetails()).thenReturn(Arrays.asList(new SubSystemDetails(SubSystem.HTTP_SERVER.name(), SubSystemStatus.STARTING.name(), SubSystem.HTTP_SERVER.getOrder(), null, null)));

        interceptor.invoke(invocation);
    }

    @Test
    public void testSubSystemSuperClassMethodNotReady() throws Throwable {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Mockito.when(invocation.getThis()).thenReturn(new NotEnforcedSubSystemService());
        Mockito.when(invocation.getMethod()).thenReturn(notEnforcedSubSystemServiceSuperClassMethod);
        Mockito.when(statusDetails.getSubSystemDetails()).thenReturn(Arrays.asList(new SubSystemDetails(SubSystem.HTTP_SERVER.name(), SubSystemStatus.STARTING.name(), SubSystem.HTTP_SERVER.getOrder(), null, null)));

        interceptor.invoke(invocation);
    }

    @Test(expected = StartupContractViolation.class)
    public void testSubSystemNotReadyEnforced() throws Throwable {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Mockito.when(invocation.getThis()).thenReturn(new EnforcedSubSystemService());
        Mockito.when(invocation.getMethod()).thenReturn(enforcedSubSystemServiceMethod);
        Mockito.when(statusDetails.getSubSystemDetails()).thenReturn(Arrays.asList(new SubSystemDetails(SubSystem.HTTP_SERVER.name(), SubSystemStatus.STARTING.name(), SubSystem.HTTP_SERVER.getOrder(), null, null)));

        interceptor.invoke(invocation);
    }

    @Test(expected = StartupContractViolation.class)
    public void testSubSystemSuperClassMethodNotReadyEnforced() throws Throwable {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Mockito.when(invocation.getThis()).thenReturn(new EnforcedSubSystemService());
        Mockito.when(invocation.getMethod()).thenReturn(enforcedSubSystemServiceSuperClassMethod);
        Mockito.when(statusDetails.getSubSystemDetails()).thenReturn(Arrays.asList(new SubSystemDetails(SubSystem.HTTP_SERVER.name(), SubSystemStatus.STARTING.name(), SubSystem.HTTP_SERVER.getOrder(), null, null)));

        interceptor.invoke(invocation);
    }

    @Test(expected = StartupContractViolation.class)
    public void testNonSubSystem() throws Throwable {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Mockito.when(invocation.getThis()).thenReturn(new Object());
        Mockito.when(invocation.getMethod()).thenReturn(Object.class.getMethod("hashCode"));
        Mockito.when(statusDetails.getSubSystemDetails()).thenReturn(Arrays.asList(new SubSystemDetails(SubSystem.HTTP_SERVER.name(), SubSystemStatus.OK.name(), SubSystem.HTTP_SERVER.getOrder(), null, null)));

        interceptor.invoke(invocation);
    }

    @SubSystemService(SubSystem.DATABASE_CLIENT)
    private class NotEnforcedSubSystemService {
        public void method() {
        }
    }

    @SubSystemService(value = SubSystem.DATABASE_CLIENT, allowUninitializedCalls = false)
    private class EnforcedSubSystemService {
        public void method() {
        }
    }
}
