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
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.CommandRunner;
import org.eblocker.server.common.system.LoggingProcess;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Executor;

@Singleton
public class CommandRunnerUnix implements CommandRunner {

    private final Executor executor;

    @Inject
    public CommandRunnerUnix(@Named("unlimitedCachePoolExecutor") Executor executor) {
        this.executor = executor;
    }

    /**
     * Runs a single command and returns the stdout as result
     */
    @Override
    public String runCommandWithOutput(String command, String... arguments) throws IOException, InterruptedException {
        String result = "";

        LoggingProcess loggingProcess = new LoggingProcessUnix(command, executor);
        loggingProcess.run(commandAndArgs(command, arguments));

        Scanner scanner;

        for (String stdout; (stdout = loggingProcess.pollStdout()) != null; ) {
            scanner = new Scanner(stdout);
            while (scanner.hasNextLine()) {
                result += scanner.nextLine() + "\n";
            }
            scanner.close();
        }

        return result.substring(0, result.length() - 1); //Remove last linefeed
    }

    /*
     * Runs a single command and returns the exit code as result
     */
    @Override
    public int runCommand(String command, String... arguments) throws IOException, InterruptedException {
        LoggingProcess loggingProcess = new LoggingProcessUnix(command, executor);
        loggingProcess.run(commandAndArgs(command, arguments));
        return loggingProcess.exitValue();
    }

    private String[] commandAndArgs(String command, String... arguments) {
        String[] commandAndArgs = new String[1 + arguments.length];
        commandAndArgs[0] = command;
        System.arraycopy(arguments, 0, commandAndArgs, 1, arguments.length);
        return commandAndArgs;
    }
}
