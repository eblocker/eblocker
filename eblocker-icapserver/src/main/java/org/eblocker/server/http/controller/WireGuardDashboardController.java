package org.eblocker.server.http.controller;

import io.netty.buffer.ByteBuf;
import org.eblocker.server.http.model.WireGuardClientConfigurationView;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.restexpress.Request;
import org.restexpress.Response;

public interface WireGuardDashboardController {

    boolean getStatus(
            Request request,
            Response response);

    WireGuardPeerView getPeer(
            Request request,
            Response response);

    WireGuardPeerView createPeer(
            Request request,
            Response response);

    void deletePeer(
            Request request,
            Response response);

    WireGuardPeerView setLanAccess(
            Request request,
            Response response);

    WireGuardClientConfigurationView getClientConfig(
            Request request,
            Response response);

    ByteBuf getPeerQrCode(
            Request request,
            Response response);
}
