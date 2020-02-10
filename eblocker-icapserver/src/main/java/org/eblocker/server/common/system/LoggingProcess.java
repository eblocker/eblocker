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

import org.eblocker.server.common.executor.NamedRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper for processes that should log their stout and stderr streams.
 */
public abstract class LoggingProcess {
	private static final Logger log = LoggerFactory.getLogger(LoggingProcess.class);

    private static final String QUIT_MARKER = LoggingProcess.class.getName() + ":QUIT_MARKER:" + Math.random();

    private final Semaphore outputReaderSemaphore = new Semaphore(2);
	private final BlockingQueue<String> stdout = new LinkedBlockingQueue<>();
    private final AtomicInteger loggerExited = new AtomicInteger();

	abstract class StreamLogger implements Runnable {
		private InputStream stream;
		protected String scriptName;

		public StreamLogger(InputStream stream, String scriptName) {
			this.stream = stream;
			this.scriptName = scriptName;
		}

		@Override
		public void run() {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			String line;
			try {
				while ((line = reader.readLine()) != null) {
					logLine(line);
					stdout.add(line);
				}
			} catch (IOException e) {
				log.error("{}: Could not read next line from stream", scriptName, e);
			} finally {
			    // only add quit marker if all stream loggers have added their lines to stdout
			    if (loggerExited.incrementAndGet() == 2) {
                    stdout.add(QUIT_MARKER);
                }
			    outputReaderSemaphore.release();
            }
		}

		protected abstract void logLine(String line);
	}
	
	class InfoStreamLogger extends StreamLogger {
		public InfoStreamLogger(InputStream stream, String scriptName) {
			super(stream, scriptName);
		}
		@Override
		protected void logLine(String line) {
			log.info("{}: {}", scriptName, line);
		}
	}

	class ErrorStreamLogger extends StreamLogger {
		public ErrorStreamLogger(InputStream stream, String scriptName) {
			super(stream, scriptName);
		}
		@Override
		protected void logLine(String line) {
			log.error("{}: {}", scriptName, line);
		}
	}

	protected final String name; // a name that appears in the log
	private final Executor executor;
	protected Process process;

	/**
	 * Creates a process
	 * @param name name to be shown at the start of each log line
	 */
	public LoggingProcess(String name, Executor executor) {
		this.name = name;
		this.executor = executor;
	}

	/**
	 * Starts a process with the given arguments
	 * @param args
	 * @throws IOException
	 */
	public void start(String[] args) throws IOException {
        try {
            outputReaderSemaphore.acquire(2);
            process = Runtime.getRuntime().exec(args);
            executor.execute(new NamedRunnable("script-" + name + "-stdout", new InfoStreamLogger(process.getInputStream(), name)));
            executor.execute(new NamedRunnable("script-" + name + "-stderr", new ErrorStreamLogger(process.getErrorStream(), name)));
        } catch (InterruptedException e) {
            log.error("interrupted", e);
            Thread.currentThread().interrupt();
        }
	}

	public void run(String[] args) throws IOException, InterruptedException {
	    start(args);
	    waitFor();
	}

	public boolean isAlive() {
		return process.isAlive();
	}

	/**
	 * Wait for the process to terminate.
	 * @return the exit value of the process
	 * @throws InterruptedException
	 */
	public int waitFor() throws InterruptedException {
	    int status = process.waitFor();
	    if (!outputReaderSemaphore.tryAcquire(2, 3, TimeUnit.SECONDS)) {
            log.warn("logging process output readers not finished in 3 seconds - output may be missing. Process: {} exit code: {}", name, status);
        }
		return status;
	}

	public abstract int getPid();

	public int exitValue() {
	    return process.exitValue();
	}

    /**
     * Retrieves next line of output, or returns {@code null} if there is none.
     */
	public String pollStdout() {
	    String value = stdout.poll();
        return value != QUIT_MARKER ? value : null; //NOSONAR: exact same marker object is expected
	}

    /**
     * Retrieves next line of output, waiting if necessary until output becomes available or returns
     * null if process has quit.
     */
    public String takeStdout() throws InterruptedException {
        String value = stdout.take();
        return value != QUIT_MARKER ? value : null; //NOSONAR: exact same marker object is expected
    }
}
