package org.eblocker.server.http.controller;

import org.restexpress.Request;
import org.restexpress.Response;
import io.netty.buffer.ByteBuf;

import java.util.Map;

public interface WireGuardServerController {

    // =========================
    // STATUS / CONTROL
    // =========================

    Map<String, Object> getStatus(Request request, Response response);

    Map<String, Object> enable(Request request, Response response);

    Map<String, Object> disable(Request request, Response response);

    Map<String, Object> deletePeer(Request request, Response response);

    io.netty.buffer.ByteBuf getPeerQrCode(org.restexpress.Request request, org.restexpress.Response response);

    // =========================
    // CONFIG
    // =========================

    Map<String, Object> getConfig(Request request, Response response);

    Map<String, Object> setConfig(Request request, Response response);

    // =========================
    // PEERS
    // =========================

    Object createPeer(Request request, Response response);
    Object getPeers(org.restexpress.Request request, org.restexpress.Response response);
    ByteBuf getPeerConfig(org.restexpress.Request request, org.restexpress.Response response);


}
