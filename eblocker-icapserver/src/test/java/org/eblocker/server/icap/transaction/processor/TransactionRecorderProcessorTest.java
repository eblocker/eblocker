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
package org.eblocker.server.icap.transaction.processor;

import org.eblocker.server.common.recorder.TransactionRecorder;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TransactionRecorderProcessorTest {

    private TransactionRecorder recorder;
    private TransactionProcessor processor;

    @Before
    public void setup() {
        recorder = Mockito.mock(TransactionRecorder.class);
        processor = new TransactionRecorderProcessor(recorder);
    }

    @Test
    public void testRequest() {
        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.isResponse()).thenReturn(true);
        processor.process(transaction);
        Mockito.verify(recorder).addTransaction(transaction, true);
    }

    @Test
    public void testResponse() {
        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.isRequest()).thenReturn(true);
        processor.process(transaction);
        Mockito.verify(recorder).addTransaction(transaction, false);
    }

}
