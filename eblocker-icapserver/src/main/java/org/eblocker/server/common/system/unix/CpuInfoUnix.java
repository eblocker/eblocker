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

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.system.CpuInfo;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class CpuInfoUnix implements CpuInfo {
	private final File cpuInfoFile;
	private final Pattern serialPattern;
	
	@Inject
	public CpuInfoUnix(@Named("system.path.cpu.info") String cpuInfoPath) {
		this.cpuInfoFile = new File(cpuInfoPath);
		this.serialPattern = Pattern.compile("Serial\\s*:\\s*([0-9a-f]+)");
	}

	@Override
	public String getSerial() {
		String cpuInfo;
		try {
			cpuInfo = FileUtils.readFileToString(cpuInfoFile);
		} catch (IOException e) {
			throw new EblockerException("Could not read CPU info", e);
		}
		Matcher matcher = serialPattern.matcher(cpuInfo);
		if (matcher.find()) {
			return matcher.group(1);
		}
		throw new EblockerException("Could not find serial in CPU info");
	}
}
