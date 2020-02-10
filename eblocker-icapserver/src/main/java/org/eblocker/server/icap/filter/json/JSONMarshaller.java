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
package org.eblocker.server.icap.filter.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eblocker.server.common.exceptions.EblockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.icap.filter.FilterStore;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONMarshaller {
	private static final Logger log = LoggerFactory.getLogger(JSONMarshaller.class);

	private static ObjectMapper mapper = new ObjectMapper();

	private static ObjectMapper getObjectMapper() {
		return mapper;
	}

	public static void marshall(FilterStore store, OutputStream out) {
		try {
			getObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, store);

		} catch (IOException e) {
			log.error("Cannot marshall filter store: {}", e.getMessage(), e);
			throw new EblockerException("Cannot marshall filter store", e);
		}
	}

	public static FilterStore unmarshall(InputStream in) {
		try {
			FilterStore store = getObjectMapper().readValue(in, FilterStore.class);
            return store;

		} catch (IOException e) {
			log.error("Cannot unmarshall filter store: {}", e.getMessage(), e);
			throw new EblockerException("Cannot unmarshall filter store", e);
		}
	}

}
