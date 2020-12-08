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
package org.eblocker.server.common.system;

import java.io.IOException;

/**
 * Runs external scripts.
 */
public interface ScriptRunner {
    /**
     * Runs a script. This method waits for the script to terminate.
     *
     * @param scriptName name of the script
     * @return return value of the script. Usually, a zero exit value indicates success, while non-zero
     * values indicate failure.
     * @throws IOException
     * @throws InterruptedException
     */
    public int runScript(String scriptName, String... arguments) throws IOException, InterruptedException;

    /**
     * Starts a script.
     * NOTE: you are responsible for calling stopScript(), otherwise there will be two threads leaking
     * per call!
     *
     * @param scriptName
     * @return the running LoggingProcess
     * @throws IOException
     */
    public LoggingProcess startScript(String scriptName, String... arguments) throws IOException;

    /**
     * Stops a script
     *
     * @param loggingProcess the process to be stopped
     * @throws IOException
     * @throws InterruptedException
     */
    public void stopScript(LoggingProcess loggingProcess) throws IOException, InterruptedException;
}
