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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class TelnetConnectionImpl implements TelnetConnection {
	private Socket socket;
	private BufferedReader controlInput;
	private BufferedWriter controlOutput;

	@Override
	public void connect(String host, int port) throws IOException {
		socket = new Socket(host, port);
        socket.setKeepAlive(true);

        //get input- and outputstream
        controlInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        controlOutput = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
	}

	@Override
	public void writeLine(String line) throws IOException {
	    if (controlOutput == null) {
	        throw new IOException("Telnet connection not properly set up or not connected.");
	    }

		controlOutput.write(line);
        controlOutput.newLine();
        controlOutput.flush();
	}

	@Override
	public String readLine() throws IOException {
	    if (controlInput == null) {
	        throw new IOException("Telnet connection not properly set up or not connected.");
	    }

	    return controlInput.readLine();
	}

	@Override
	public void close() throws IOException {
	    if (socket != null) {
	        socket.close();
	    }
	    if (controlInput != null) {
	        controlInput.close();
	    }
	    if (controlOutput != null) {
	        controlOutput.close();
	    }
	}

	@Override
	public List<String> readAvailableLines() throws IOException {
		List<String> lines = new ArrayList<>();
		while (isLineAvailableToRead()) {
			lines.add(readLine());
		}
		return lines;
	}

	@Override
	public boolean isConnected() {
		return socket != null && socket.isConnected() && controlInput != null && controlOutput != null;
	}

	@Override
	public boolean isLineAvailableToRead() throws IOException {
		return controlInput != null && controlInput.ready();
	}
}
