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

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.exceptions.ServiceNotAvailableException;
import org.eblocker.server.http.controller.wrapper.test.ControllerA;
import org.eblocker.server.http.controller.wrapper.test.ControllerAImpl;
import org.eblocker.server.http.controller.wrapper.test.ControllerB;
import org.eblocker.server.http.controller.wrapper.test.ControllerBImpl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ControllerWrapperFactoryTest {

    @Test
    public void test() {

        //
        // Create wrappers
        //
        ControllerA ctrlA = ControllerWrapperFactory.wrap(ControllerA.class);
        ControllerB ctrlB = ControllerWrapperFactory.wrap(ControllerB.class);

        List<String> out = new ArrayList<>();

        //
        // Both wrapper have no real controller yet --> expect exceptions for each call.
        //
        try {
            ctrlA.get("Hello", 17, out);
            fail("Expected ServiceNotAvailableException");

        } catch (ServiceNotAvailableException e) {
            // ok.
        }

        try {
            ctrlB.start("Hello", 17L, out);
            fail("Expected ServiceNotAvailableException");

        } catch (ServiceNotAvailableException e) {
            // ok.
        }

        //
        // Inject real controller into "A".
        //
        ((ControllerWrapper<ControllerAImpl>) ctrlA).setControllerImpl(new ControllerAImpl());

        //
        // "A" should process calls now.
        //
        String result = ctrlA.get("Hello", 17, out);
        assertTrue(result.startsWith("get"));
        assertTrue(out.contains(result));

        //
        // "B" should still fail
        //
        try {
            ctrlB.start("Hello", 17L, out);
            fail("Expected ServiceNotAvailableException");

        } catch (ServiceNotAvailableException e) {
            // ok.
        }

        //
        // Inject real controller into "B".
        //
        ((ControllerWrapper<ControllerBImpl>) ctrlB).setControllerImpl(new ControllerBImpl());

        //
        // "A" should still process calls now.
        //
        result = ctrlA.get("Hello", 18, out);
        assertTrue(result.startsWith("get"));
        assertTrue(out.contains(result));

        //
        // "B" should also process calls.
        //
        result = ctrlB.start("Hello", 19L, out);
        assertTrue(result.startsWith("start"));
        assertTrue(out.contains(result));

        //
        // Exception from real controller should be passed "as is"
        //
        try {
            ctrlA.post("Hello", 37, out);
            fail("Expected EblockerException");

        } catch (EblockerException e) {
            // ok.

        }
    }

}
