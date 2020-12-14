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
package org.eblocker.server.junit;

import org.junit.Assert;

public class Assert5 {

    /**
     * Poor man's implementation of JUnit5's Assert.assertThrows.
     *
     * <p>
     * Can be used as an alternative to annotating tests with {@code @Test(expected=SomeException.class}
     * to allow more dense tests.
     * </p>
     * <pre>
     * {@code
     * @Test
     * public void testExceptions() {
     *     Assert5.assertThrows(FileNotFoundException.class, db.init("/does/not/exist"));
     *     Assert5.assertThrows(IOException.class, db.init('/corrupt/file"));
     * }
     * }
     * </pre>
     */
    public static void assertThrows(Class<? extends Exception> exceptionClass, Executable executable) {
        try {
            executable.execute();
            Assert.fail("No exception thrown.");
        } catch (Exception e) {
            if (exceptionClass.isInstance(e)) {
                return;
            }
            Assert.fail("Unexpected type of exception " + e.getClass());
        }
    }

    public interface Executable {
        void execute() throws Exception;
    }
}
