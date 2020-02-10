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

import java.io.IOException;

import org.eblocker.server.http.controller.FactoryResetController;

import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.common.system.ScriptRunner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class FactoryResetControllerImpl implements FactoryResetController {
	private static final Logger log = LoggerFactory.getLogger(FactoryResetControllerImpl.class);
	private ScriptRunner scriptRunner;
	private String factoryResetScript;

	@Inject
    public FactoryResetControllerImpl(ScriptRunner scriptRunner,
            @Named("factory.reset.command") String factoryResetScript) {
		this.scriptRunner=scriptRunner;
		this.factoryResetScript = factoryResetScript;
	}

    @Override
    public void factoryReset(Request request, Response response) throws IOException, InterruptedException {
        log.info("Requested factory reset");
        scriptRunner.runScript(factoryResetScript);
        log.info("Completed factory reset" + factoryResetScript);
    }

}
