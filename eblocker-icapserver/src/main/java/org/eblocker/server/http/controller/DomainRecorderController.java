package org.eblocker.server.http.controller;

import org.eblocker.server.common.recorder.RecordedDomainCounter;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.Map;

public interface DomainRecorderController {
    Map<String, RecordedDomainCounter> getRecordedDomains(Request request, Response response);
    void resetRecording(Request request, Response response);
}
