package org.eblocker.server.http.controller;

import org.eblocker.server.common.recorder.RecordedDomainRequests;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.List;

public interface DomainRecorderController {
    List<RecordedDomainRequests> getRecordedDomains(Request request, Response response);
    void resetRecording(Request request, Response response);
}
