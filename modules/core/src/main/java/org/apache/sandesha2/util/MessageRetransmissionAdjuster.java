/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sandesha2.util;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.policy.SandeshaPolicyBean;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.beans.SenderBean;

/**
 * This is used to adjust retransmission infoamation after each time the message
 * is sent.
 */

public class MessageRetransmissionAdjuster {

	private static final Log log = LogFactory.getLog(MessageRetransmissionAdjuster.class);
	
	public static final int NUMBER_OF_EAGER_RESENDS_ALLOWED = 3; //the number of msg resends until we slow down our pace
	public static final int LAZY_RESEND_SCALE_FACTOR = 10; //lazy resends are 10 times slower than eager resends

	public static boolean adjustRetransmittion(RMMsgContext rmMsgCtx, SenderBean retransmitterBean, ConfigurationContext configContext,
			StorageManager storageManager) throws AxisFault {

		if (LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled())
			log.debug("Enter: MessageRetransmissionAdjuster::adjustRetransmittion");

		String internalSequenceID = retransmitterBean.getInternalSequenceID();
		String sequenceID = retransmitterBean.getSequenceID();

		rmMsgCtx.setProperty(Sandesha2Constants.MessageContextProperties.INTERNAL_SEQUENCE_ID,internalSequenceID);
		rmMsgCtx.setProperty(Sandesha2Constants.MessageContextProperties.SEQUENCE_ID, sequenceID);
		
		// operation is the lowest level Sandesha2 could be attached.
		SandeshaPolicyBean propertyBean = SandeshaUtil.getPropertyBean(rmMsgCtx.getMessageContext().getAxisOperation());

		retransmitterBean.setSentCount(retransmitterBean.getSentCount() + 1);
		adjustNextRetransmissionTime(retransmitterBean, propertyBean);

		int maxRetransmissionAttempts = propertyBean.getMaximumRetransmissionCount();
		
		// We can only time out sequences if we can identify the correct sequence, and
		// we need the internal sequence id for that.
		boolean continueSending = true;
		if(internalSequenceID != null) {
			boolean timeOutSequence = false;
			if (maxRetransmissionAttempts >= 0 && retransmitterBean.getSentCount() > maxRetransmissionAttempts)
				timeOutSequence = true;

			if (timeOutSequence) {
	
				retransmitterBean.setSend(false);

				// Warn the user that the sequence has timed out
				//if (log.isWarnEnabled())
				//	log.warn();

				// Only messages of outgoing sequences get retransmitted. So named
				// following method according to that.
				
				SequenceManager.finalizeTimedOutSequence(internalSequenceID, rmMsgCtx.getMessageContext(), storageManager);
				continueSending = false;
			}
		}

		if (LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled())
			log.debug("Exit: MessageRetransmissionAdjuster::adjustRetransmittion, " + continueSending);
		return continueSending;
	}

	/**
	 * This sets the next time the message has to be retransmitted. This uses
	 * the base retransmission interval and exponentialBackoff properties to
	 * calculate the correct time.
	 * 
	 * @param retransmitterBean
	 * @param policyBean
	 * @return
	 */
	private static SenderBean adjustNextRetransmissionTime(SenderBean retransmitterBean, SandeshaPolicyBean propertyBean) throws SandeshaException {

		int count = retransmitterBean.getSentCount();

		long baseInterval = propertyBean.getRetransmissionInterval();

		long newInterval = baseInterval;
		if (propertyBean.isExponentialBackoff()) {
			newInterval = generateNextExponentialBackedoffDifference(count, baseInterval);
		}		
		else if(retransmitterBean.getSentCount()>NUMBER_OF_EAGER_RESENDS_ALLOWED){
			//if we are not exponential backoff then we should still do a straight backoff after a set number
			//of resends
			newInterval*=LAZY_RESEND_SCALE_FACTOR;
		}

		long timeNow = System.currentTimeMillis();
		long newTimeToSend = timeNow + newInterval;
		retransmitterBean.setTimeToSend(newTimeToSend);

		return retransmitterBean;
	}

	private static long generateNextExponentialBackedoffDifference(int count, long initialInterval) {
		long interval = initialInterval;
		for (int i = 1; i < count; i++) {
			interval = interval * 2;
		}

		return interval;
	}

}
