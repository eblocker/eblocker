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

import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.registration.ProductFeature;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class ForwardDecisionProcessor implements TransactionProcessor {
	@SuppressWarnings("unused")
	private final static Logger log = LoggerFactory.getLogger(ForwardDecisionProcessor.class);
	private final static Logger FILTER_LOG = LoggerFactory.getLogger("FILTER_LOG");

	@Override
	public boolean process(Transaction transaction) {
	    Session session = transaction.getSession();
		Decision decision = session.popForwardDecision(transaction.getUrl());
		if (decision != Decision.NO_DECISION) {
			FILTER_LOG.info("{}\tFW\t{}\t{}\t{}", session.getShortId(), decision, transaction.getUrl(), "<<FORWARD-DECISION>>");
		}

		switch (decision) {

			case BLOCK:
                transaction.block();
                return false;

			case PASS:
				return false;

			default:
				return true;

		}
	}
}
