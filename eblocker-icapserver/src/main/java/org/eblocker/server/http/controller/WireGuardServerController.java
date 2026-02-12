package org.eblocker.server.http.controller;

import io.netty.buffer.ByteBuf;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.Map;

/**
 * Minimal controller interface for WireGuard server endpoints.
 * RestExpress will serialize returned objects to JSON.
 */
public interface WireGuardServerController {

    Map<String, Object> getStatus(Request request, Response response);

    Map<String, Object> enable(Request request, Response response);

    Map<String, Object> disable(Request request, Response response);

    Map<String, Object> getConfig(Request request, Response response);

    Map<String, Object> setConfig(Request request, Response response);

    Object createPeer(Request request, Response response);

    Object getPeers(Request request, Response response);

    ByteBuf getPeerConfig(Request request, Response response);

    ByteBuf getPeerQr(Request request, Response response);

    Map<String, Object> deletePeer(Request request, Response response);
}
