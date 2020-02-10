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
package org.eblocker.server.http.controller.impl;

import java.util.List;

import org.eblocker.server.http.controller.RecordingController;
import org.eblocker.server.common.data.RecordedUrl;
import org.eblocker.server.common.util.StartRecordingRequestData;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.http.service.RecordingService;
import com.google.inject.Inject;

public class RecordingControllerImpl implements RecordingController {
	private static final Logger log = LoggerFactory.getLogger(RecordingControllerImpl.class);

	RecordingService recordingService;
		
	@Inject
	public RecordingControllerImpl(RecordingService recordingService
			) {
		this.recordingService = recordingService;
	}

	@Override
	public boolean recordingStartStop(Request request, Response resp) {
		StartRecordingRequestData requestData = request.getBodyAs(StartRecordingRequestData.class);
		return this.recordingService.recordingStartStop(requestData);
	}

	@Override
	public boolean getRecordingStatus(Request request, Response resp) {
		log.info("getRecordingStatus()");
		return this.recordingService.getRecordingStatus();
	}

	@Override
	public List<RecordedUrl> getRecordedDomainList(Request request, Response response){
		return this.recordingService.getRecordedDomainList();
	}
	
}
