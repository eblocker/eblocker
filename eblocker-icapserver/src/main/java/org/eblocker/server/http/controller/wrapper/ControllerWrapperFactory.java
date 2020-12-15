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
package org.eblocker.server.http.controller.wrapper;

import org.eblocker.server.common.exceptions.ServiceNotAvailableException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ControllerWrapperFactory<CTRL, IMPL extends CTRL> implements InvocationHandler {

    private IMPL controllerImpl;

    private ControllerWrapperFactory() {
    }

    public static <CTRL> CTRL wrap(Class<CTRL> clazz) {
        return (CTRL) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{ clazz, ControllerWrapper.class }, new ControllerWrapperFactory<>());
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("setControllerImpl")) {
            controllerImpl = (IMPL) args[0];
            return null;

        }
        if (controllerImpl != null) {
            try {
                return method.invoke(controllerImpl, args);

            } catch (InvocationTargetException e) {
                throw e.getCause();
            }

        } else {
            throw new ServiceNotAvailableException("Controller method " + method.getName() + " not yet available");
        }
    }

}
