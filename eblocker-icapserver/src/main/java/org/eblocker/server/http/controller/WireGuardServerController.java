package org.eblocker.server.http.controller;

import org.restexpress.Request;
import org.restexpress.Response;

import java.util.Map;

public interface WireGuardServerController {
    Map<String, Object> getStatus(Request request, Response response);
}
