package org.eblocker.server.http.controller;

import io.netty.buffer.ByteBuf;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.http.model.WireGuardClientConfigurationView;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.eblocker.server.http.model.WireGuardServerStatusView;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.List;

public interface WireGuardServerController {

    WireGuardServerStatusView getStatus(
            Request request,
            Response response);

    WireGuardServerStatusView enable(
            Request request,
            Response response);

    WireGuardServerStatusView disable(
            Request request,
            Response response);

    List<WireGuardPeerView> getPeers(
            Request request,
            Response response);

    WireGuardPeerView createPeer(
            Request request,
            Response response);

    boolean deletePeer(
            Request request,
            Response response);

    WireGuardPeerView setLanAccess(
            Request request,
            Response response);

    WireGuardEndpointConfig getEndpointConfig(
            Request request,
            Response response);

    WireGuardEndpointConfig setEndpointConfig(
            Request request,
            Response response);

    WireGuardClientConfigurationView getClientConfig(
            Request request,
            Response response);

    ByteBuf getPeerQrCode(
            Request request,
            Response response);
}
