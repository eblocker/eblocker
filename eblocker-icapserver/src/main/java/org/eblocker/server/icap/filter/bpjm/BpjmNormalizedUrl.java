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
package org.eblocker.server.icap.filter.bpjm;

public class BpjmNormalizedUrl {

	private final String domain;
	private final String path;
	private final int depth;
	
	BpjmNormalizedUrl(String domain, String path, int depth) {
		this.domain = domain;
		this.path = path;
		this.depth = depth;
	}

	public String getDomain() {
		return domain;
	}

	public String getPath() {
		return path;
	}

	public int getDepth() {
		return depth;
	}

}
