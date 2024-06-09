package org.eblocker.server.app;

import org.eblocker.server.common.EblockerModule;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.status.StartupStatusReporter;
import org.eblocker.server.http.service.SystemStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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

    @Nested
    class BootPhase {
        @BeforeEach
        void setUp() {
            doNothing().when(sut).exitSystem();
        }

        @Test
        void bootPhase_OnThrowable_Early() throws IOException {
            //Given
            doThrow(RuntimeException.class).when(sut).initBootPhase(any());

            try {
                //When
                sut.bootPhase(new EblockerModuleMock());

                //Then
            } catch (Throwable e) {
                fail();
            }
        }

        @Test
        void bootPhase_OnThrowable_late() throws IOException {
            //Given
            doAnswer(invocationOnMock -> {
                invocationOnMock.callRealMethod();
                throw new RuntimeException();
            }).when(sut).startHttpServer();
            when(systemStatusService.starting(any())).thenReturn(systemStatusService);

            //When
            sut.bootPhase(new EblockerModuleMock());

            //Then
            verify(systemStatusService).setExecutionState(ExecutionState.ERROR);
            verify(systemStatusService).error(SubSystem.EBLOCKER_CORE);
            verify(startupStatusReporter).startupFailed(any());

        }
    }

    @Nested
    class runtimePhase {

        @BeforeEach
        void setUp() throws IOException {
            when(systemStatusService.starting(any())).thenReturn(systemStatusService);
            sut.bootPhase(new EblockerModuleMock());
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

    private class EblockerModuleMock extends EblockerModule {

        public EblockerModuleMock() throws IOException {
        }

        @Override
        protected void configure() {
            super.configure();
            bind(SystemStatusService.class).toInstance(systemStatusService);
            bind(StartupStatusReporter.class).toInstance(startupStatusReporter);
        }
    }
}