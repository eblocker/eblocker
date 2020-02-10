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

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.service.FeatureServicePublisher;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.AnonymousController;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.AnonymousService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.TorCheckService;
import com.google.inject.Inject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Provides access to anonymous configuration
 */
public class AnonymousControllerImpl extends SessionContextController implements AnonymousController {

	private static final Logger log = LoggerFactory.getLogger(AnonymousControllerImpl.class);
	private final TorController torControl;
	private final DeviceService deviceService;
	private final FeatureServicePublisher featureService;
    private final TorCheckService torCheckService;
    private final AnonymousService anonymousService;

	@Inject
	public AnonymousControllerImpl(SessionStore sessionStore, PageContextStore pageContextStore,
                                   DeviceService deviceService,
                                   TorController torControl,
                                   TorCheckService torCheckService,
                                   FeatureServicePublisher featureService,
                                   AnonymousService anonymousService) {
		super(sessionStore, pageContextStore);
		this.torControl=torControl;
		this.deviceService = deviceService;
		this.torCheckService = torCheckService;
		this.featureService = featureService;
		this.anonymousService = anonymousService;
	}

	@Override
	public Object getConfig(Request request, Response response) {
		Map<String, Boolean> result = new HashMap<>();
		Session session = getSession(request);
		Device device = deviceService.getDeviceById(session.getDeviceId());
        return getDeviceConfig(device);
	}

	@Override
	public Object getConfigById(Request request, Response response) {
        String deviceId = request.getHeader("deviceId");
        Device device = deviceService.getDeviceById(deviceId);
		return getDeviceConfig(device);
	}

	private Object getDeviceConfig(Device device) {
        Map<String, Boolean> result = new HashMap<>();
        if(device != null){
            boolean routedThroughTor = device.isUseAnonymizationService() && device.isRoutedThroughTor();
            log.info("Device with IPs: {} is routed through Tor: {}", device.getIpAddresses(), routedThroughTor);
            result.put("sessionUseTor", routedThroughTor);
            return result;
        }
        return null;
    }

	@Override
	public Object putConfig(Request request, Response response) {
		@SuppressWarnings("unchecked")
		Map<String, Boolean> config = request.getBodyAs(Map.class);

		Session session = getSession(request);
		boolean useTor = config.get("sessionUseTor");

		Device device = deviceService.getDeviceById(session.getDeviceId());

		if(useTor){//all sessions of this device are using Tor
			anonymousService.enableTor(device);
		    log.info("Device with IP {} will now be routed through Tor...",session.getIp());
		} else{//sessions of this device not using Tor (anymore)
			anonymousService.disableTor(device);
		    log.info("Device with IP {} will now NOT be routed through Tor...",session.getIp());
		}
		return config;
	}

    @Override
    public Object putConfigById(Request request, Response response) {
        @SuppressWarnings("unchecked")
        Map<String, Boolean> config = request.getBodyAs(Map.class);
        String deviceId = request.getHeader("deviceId");
        Device device = deviceService.getDeviceById(deviceId);
        boolean useTor = config.get("sessionUseTor");

        if(useTor){//all sessions of this device are using Tor
            anonymousService.enableTor(device);
            log.info("Device with IP {} will now be routed through Tor...",device.getIpAddresses());
        } else{//sessions of this device not using Tor (anymore)
            anonymousService.disableTor(device);
            log.info("Device with IP {} will now NOT be routed through Tor...",device.getIpAddresses());
        }
        return config;
    }

	@Override
	public boolean isTorConnected(Request req, Response resp){
		return torControl.isConnectedToTorNetwork();//uses buffer here
	}

	@Override
	public void getNewTorIdentity(Request req, Response resp){
		log.info("Tor is getting a new identity...");
		torControl.getNewIdentity();
	}

	@Override
	public Object getTorCountries(Request req, Response resp){
		return torControl.getCountryList();
	}

	@Override
	public void setTorExitNodeCountries(Request req, Response resp){
		Set<String> countries = req.getBodyAs(HashSet.class);
		log.debug("Selected Tor exit node countries:");

		for(String country : countries){
			log.debug("{}",country);
		}
		torControl.setAllowedExitNodesCountries(countries);
	}

	@Override
	public Object getCurrentTorExitNodeCountries(Request req, Response resp){
		return torControl.getCurrentExitNodeCountries();
	}

	@Override
	public Object getTorCheckServices(Request request, Response response) {
	    return torCheckService.getSites(true);
	}

	@Override
	public void setWebRTCBlockingState(Request request, Response response){
		Map<String, Boolean> map = request.getBodyAs(Map.class);
		boolean webRTCEnabled = map.get("webRTCBlockEnabled");
		featureService.setWebRTCBlockingState(webRTCEnabled);
		log.info("Set WebRTC state: {}",webRTCEnabled);
	}

	@Override
	public boolean isWebRTCBlockingEnabled(Request request, Response response){
		return featureService.getWebRTCBlockingState();
	}

	@Override
	public void setHTTPRefererRemovingState(Request request, Response response){
		Map<String, Boolean> map = request.getBodyAs(Map.class);
		boolean status = map.get("httpRemovingEnabled");
		featureService.setHTTPRefererRemovingState(status);
	}

	@Override
	public boolean isHTTPRefererRemovingEnabled(Request request, Response response){
		return featureService.getHTTPRefererRemovingState();
	}

	@Override
	public boolean getGoogleCaptivePortalRedirectState(Request req, Response resp){
		return featureService.getGoogleCaptivePortalRedirectorState();
	}

	@Override
	public void setGoogleCaptivePortalRedirectState(Request req, Response resp){
		Map<String, Boolean> map = req.getBodyAs(Map.class);
		boolean state = map.get("captivePortalResponderEnabled");
		log.info("Set GoogleCaptivePortalRedirector state: {}",state);
		featureService.setGoogleCaptivePortalRedirectorState(state);
	}

    @Override
    public boolean getDntHeaderState(Request request, Response response) {
        return featureService.getDntHeaderState();
    }

    @Override
    public void setDntHeaderState(Request request, Response response) {
        Map<String, Boolean> map = request.getBodyAs(Map.class);
        boolean state = map.get("dntHeaderEnabled");
        log.info("Set DNTHeader state: {}", state);
        featureService.setDntHeaderState(state);
    }

}
