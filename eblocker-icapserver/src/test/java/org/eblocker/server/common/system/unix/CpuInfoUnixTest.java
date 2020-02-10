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

import static org.junit.Assert.*;

import java.net.URISyntaxException;
import java.net.URL;

import org.junit.Test;

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.system.CpuInfo;

public class CpuInfoUnixTest {
	private CpuInfo getCpuInfo(String resource) throws URISyntaxException {
		URL url = ClassLoader.getSystemResource(resource);
		return new CpuInfoUnix(url.toURI().getPath());
	}

	@Test
	public void serial() throws Exception {
		CpuInfo info = getCpuInfo("test-data/cpuinfo.txt");
		String serial = info.getSerial();
		assertEquals(serial, "4441551637373137020001359d840f07");
	}

	@Test(expected= EblockerException.class)
	public void serial404() throws Exception {
		CpuInfo info = getCpuInfo("test-data/cpuinfo-noserial.txt");
		info.getSerial();
	}
}
