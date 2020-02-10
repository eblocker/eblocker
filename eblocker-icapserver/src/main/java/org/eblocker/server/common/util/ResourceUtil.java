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
package org.eblocker.server.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.eblocker.server.common.exceptions.EblockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceUtil {
	private static final Logger log = LoggerFactory.getLogger(ResourceUtil.class);

	public static String loadResource(String name) {
		try {
			ClassLoader classLLoader = ClassLoader.getSystemClassLoader();
			InputStream in = classLLoader.getResourceAsStream(name);
			if (in == null) {
				String msg = "Cannot load resource "+name;
				log.error(msg);
				throw new EblockerException(msg);
			}
			return IOUtils.toString(in);
		} catch (IOException e) {
			String msg = "Cannot open resource file "+name+": "+e.getMessage();
			log.error(msg);
			throw new EblockerException(msg, e);
		}
	}
	
	public static String loadResource(String name, Map<String,String> context) {
		String resource = loadResource(name);
		for (Entry<String,String> entry: context.entrySet()) {
			//TODO: Cache regex pattern for re-use
			resource = resource.replaceAll(entry.getKey(), entry.getValue());
		}
		return resource;
	}

}
