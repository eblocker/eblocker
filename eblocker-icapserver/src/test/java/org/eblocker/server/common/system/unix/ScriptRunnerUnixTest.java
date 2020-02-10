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

import org.eblocker.server.common.system.LoggingProcess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

public class ScriptRunnerUnixTest {

    private Executor executor;
    private ScriptRunnerUnix runner;

	@Before
	public void setUp() throws Exception {
	    executor = Executors.newFixedThreadPool(2);
		String wrapperPath = ClassLoader.getSystemResource("test-data/script-runner/scriptWrapper.sh").toURI().getPath();
		new File(wrapperPath).setExecutable(true); // Maven seems to ignore file permissions when copying test resources
		runner = new ScriptRunnerUnix(wrapperPath, "killCommand", executor);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test(expected=IOException.class)
	public void wrapperNotFound() throws Exception {
		new ScriptRunnerUnix("noWrapper", "killCommand", executor).runScript("noScript");
	}
	
	@Test
	public void testReturnValue() throws Exception {
		int retVal = runner.runScript("foobar");
		assertEquals(42, retVal);
	}

	@Test(expected=NullPointerException.class)
	public void testNullArgument() throws Exception {
		runner.runScript(null);
	}

	@Test
	public void testStopScript() throws IOException, InterruptedException {
	    LoggingProcess p = Mockito.mock(LoggingProcess.class);
	    runner.stopScript(p);
	}

}
