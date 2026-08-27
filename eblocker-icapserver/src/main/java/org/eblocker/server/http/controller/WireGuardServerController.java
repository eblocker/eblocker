package org.eblocker.server.http.controller;

import org.restexpress.Request;
import org.restexpress.Response;
import io.netty.buffer.ByteBuf;
import org.eblocker.server.http.model.WireGuardStatus;


import java.util.Map;

public interface WireGuardServerController {

    // =========================
    // STATUS / CONTROL
    // =========================

    WireGuardStatus getStatus(Request request, Response response);

    WireGuardStatus enable(Request request, Response response);

    WireGuardStatus disable(Request request, Response response);

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
