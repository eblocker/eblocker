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
package org.eblocker.server.common.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class MockTorControllerConnection implements TelnetConnection {
	private boolean connected = false;
	private boolean authenticated = false;
	private boolean hasCircuit = true;
	private boolean enoughDirInfo = true;
	private boolean hasReceivedReloadSignal = false;
	private boolean hasReceivedNewnymSignal = false;
	private int bootstrapPhasePercentage = 100;
	private Queue<String> responses = new ArrayBlockingQueue<String>(16);

	@Override
	public void connect(String host, int port) throws IOException {
		if (host.equals("localhost") && port == 9051) {
			connected = true;
		} else {
			throw new IOException("Connection failed");
		}
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public boolean isLineAvailableToRead() throws IOException {
		return responses != null && responses.size()>0;
	}

	@Override
	public void writeLine(String line) throws IOException {
		if (! connected) {
			throw new IOException("Not connected");
		}

		// process command:
		if (line.startsWith("authenticate ")) {
			authenticated = true;
			responses.add(TorController.RESPONSE_OK);
			
		} else if (line.equals("getinfo " + TorController.STATUS_CIRCUIT_ESTABLISHED)) {
			assert(authenticated);
			responses.add("250-" + TorController.STATUS_CIRCUIT_ESTABLISHED + "=" + (hasCircuit ? "1" : "0"));
			responses.add(TorController.RESPONSE_OK);
			
		} else if (line.equals("getinfo " + TorController.STATUS_ENOUGH_DIR_INFO)) {
            assert(authenticated);
            responses.add("250-" + TorController.STATUS_ENOUGH_DIR_INFO + "=" + (enoughDirInfo ? "1" : "0"));
            responses.add(TorController.RESPONSE_OK);
            
		} else if (line.equals("getinfo " + TorController.STATUS_BOOTSTRAP_PHASE)) {
			assert(authenticated);
			responses.add("250-status/bootstrap-phase=NOTICE BOOTSTRAP PROGRESS="+bootstrapPhasePercentage+" Testsuffix");
			responses.add(TorController.RESPONSE_OK);
			
		} else if (line.equals(TorController.SUBSCRIBE_STATUS_CLIENT_EVENTS)) {
			assert(authenticated);
			responses.add(TorController.RESPONSE_OK);
			
		} else if (line.equals(TorController.RECONFIGURE_COMMAND)) {
		    assert(authenticated);
		    hasReceivedReloadSignal = true;
		    responses.add(TorController.RESPONSE_OK);
		    
        } else if (line.equals(TorController.NEW_IDENTITY_COMMAND)) {
            assert(authenticated);
            hasReceivedNewnymSignal = true;
            responses.add(TorController.RESPONSE_OK);

        } else {
			throw new IOException("Did not understand the command: " + line);
		}
	}

	@Override
	public String readLine() throws IOException {
		if (! connected) {
			throw new IOException("Not connected");
		}

		return responses.poll();
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public List<String> readAvailableLines() throws IOException {
		List<String> lines = new ArrayList<>();
		while (isLineAvailableToRead()) {
			lines.add(readLine());
		}
		return lines;
	}

	public void setHasCircuit(boolean hasCircuit) {
		this.hasCircuit = hasCircuit;
	}

	public void setBootstrapPhasePercentage(int percentage){
	    this.bootstrapPhasePercentage = percentage;
    }

	/**
	 * Useful for simulating that Tor has been stopped or restarted
	 */
	public void disconnect() {
	    connected = false;
	}

	/**
	 * Query whether a reload signal has been received successfully (i.e. in a connected and authenticated state)
	 * @return
	 */
	public boolean hasReceivedReloadSignal() {
	    return hasReceivedReloadSignal;
	}
	
	public void resetHasReceivedReloadSignal() {
	    hasReceivedReloadSignal = false;
	}
	
	public boolean hasReceivedNewnymSignal() {
	    return hasReceivedNewnymSignal;
	}
	
	public void resetHasReceivedNewnymSignal() {
	    hasReceivedNewnymSignal = false;
	}
	
	public void sendEvent(String eventString){
		if(eventString != null){
			responses.add(eventString);
		}
	}

}
