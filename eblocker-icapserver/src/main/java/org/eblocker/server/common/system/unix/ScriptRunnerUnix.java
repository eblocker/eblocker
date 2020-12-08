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
package org.eblocker.server.common.system.unix;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * Runs a script with a sudo wrapper.
 * <p>
 * Since we can not send the SIGTERM signal to the script in order to stop it
 * (the script runs as root, this server does not), we need a special kill script.
 */
public class ScriptRunnerUnix implements ScriptRunner {
    private final String scriptWrapperPath;
    private final String killProcessCommand;
    private final Executor executor;

    @Inject
    public ScriptRunnerUnix(@Named("script.wrapper.path") String scriptWrapperPath,
                            @Named("kill.process.command") String killProcessCommand,
                            @Named("unlimitedCachePoolExecutor") Executor executor
    ) {
        this.scriptWrapperPath = scriptWrapperPath;
        this.killProcessCommand = killProcessCommand;
        this.executor = executor;
    }

    @Override
    public int runScript(String scriptName, String... arguments) throws IOException, InterruptedException {
        LoggingProcess p = startScript(scriptName, arguments);
        return p.waitFor();
    }

    @Override
    public LoggingProcess startScript(String scriptName, String... arguments) throws IOException {
        LoggingProcess p = new LoggingProcessUnix(scriptName, executor);
        p.start(createScriptWrapperArguments(scriptName, arguments));
        return p;
    }

    @Override
    public void stopScript(LoggingProcess loggingProcess) throws IOException, InterruptedException {
        int pid = loggingProcess.getPid();
        String[] args = {scriptWrapperPath, killProcessCommand, Integer.toString(pid)};
        LoggingProcess p = new LoggingProcessUnix(killProcessCommand, executor);
        p.start(args);
        p.waitFor();
    }

    private String[] createScriptWrapperArguments(String scriptName, String... arguments) {
        String[] args = new String[2 + arguments.length];
        args[0] = scriptWrapperPath;
        args[1] = scriptName;
        System.arraycopy(arguments, 0, args, 2, arguments.length);
        return args;
    }
}
