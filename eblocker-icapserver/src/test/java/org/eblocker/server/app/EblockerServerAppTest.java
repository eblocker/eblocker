package org.eblocker.server.app;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.JedisConsistencyCheck;
import org.eblocker.server.common.data.RedisBackupService;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemServiceIndex;
import org.eblocker.server.common.status.StartupStatusReporter;
import org.eblocker.server.http.service.ShutdownService;
import org.eblocker.server.http.service.SystemStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EblockerServerAppTest {

    @Spy
    private EblockerServerApp sut;

    @Mock
    private SystemStatusService systemStatusService;
    @Mock
    private StartupStatusReporter startupStatusReporter;

    private Module module;

    @BeforeEach
    void setUp() {
        module = new EblockerModuleMock();
    }

    @Nested
    class BootPhase {
        @BeforeEach
        void setUp() {
            doNothing().when(sut).exitSystem();
        }

        @Test
        void bootPhase_OnThrowable_Early() {
            //Given
            doThrow(RuntimeException.class).when(sut).initBootPhase(any());

            try {
                //When
                sut.bootPhase(module);

                //Then
            } catch (Throwable e) {
                fail();
            }
        }

        @Test
        void bootPhase_OnThrowable_late() {
            //Given
            doAnswer(invocationOnMock -> {
                invocationOnMock.callRealMethod();
                throw new RuntimeException();
            }).when(sut).startHttpServer();
            when(systemStatusService.starting(any())).thenReturn(systemStatusService);

            //When
            sut.bootPhase(module);

            //Then
            verify(systemStatusService).setExecutionState(ExecutionState.ERROR);
            verify(systemStatusService).error(SubSystem.EBLOCKER_CORE);
            verify(startupStatusReporter).startupFailed(any());

        }
    }

    @Nested
    class runtimePhase {

        @BeforeEach
        void setUp() {
            when(systemStatusService.starting(any())).thenReturn(systemStatusService);
            sut.bootPhase(module);
        }

        @Test
        void runtimePhase_OnException_early() {
            //Given
            doThrow(RuntimeException.class).when(sut).initRuntimePhase();

            //When
            sut.runtimePhase();

            //Then
            verify(systemStatusService).setExecutionState(ExecutionState.ERROR);
            verify(systemStatusService).error(SubSystem.EBLOCKER_CORE);
            verify(startupStatusReporter).startupFailed(any());
        }

        @Test
        void runtimePhase_OnException_late() {
            //Given
            doReturn("anyVersion").when(sut).doCheckSchemaVersion();

            doAnswer(invocationOnMock -> {
                invocationOnMock.callRealMethod();
                throw new RuntimeException();
            }).when(sut).startupCompleted();

            //When
            sut.runtimePhase();

            //Then
            verify(systemStatusService).setExecutionState(ExecutionState.ERROR);
            verify(systemStatusService).error(SubSystem.EBLOCKER_CORE);
            verify(startupStatusReporter).startupFailed(any());
        }
    }

    private class EblockerModuleMock extends AbstractModule {

        public EblockerModuleMock() {
        }

        @Override
        protected void configure() {
            super.configure();
            bind(SystemStatusService.class).toInstance(systemStatusService);
            bind(StartupStatusReporter.class).toInstance(startupStatusReporter);
            bind(SubSystemServiceIndex.class).toInstance(mock(SubSystemServiceIndex.class));
            bind(EventLogger.class).toInstance(mock(EventLogger.class));
            bind(ShutdownService.class).toInstance(mock(ShutdownService.class));
            bind(String.class).annotatedWith(Names.named("project.version")).toInstance("anyVersion");
            bind(String.class).annotatedWith(Names.named("httpPort")).toInstance("80");
            bind(DataSource.class).toInstance(mock(DataSource.class));
            bind(RedisBackupService.class).toInstance(mock(RedisBackupService.class));
            bind(JedisConsistencyCheck.class).toInstance(mock(JedisConsistencyCheck.class));
        }
    }
}