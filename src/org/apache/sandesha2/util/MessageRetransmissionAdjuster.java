/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.sandesha2.util;

import org.apache.axis2.context.MessageContext;
import org.apache.derby.iapi.sql.dictionary.ConsInfo;
import org.apache.sandesha2.Constants;
import org.apache.sandesha2.policy.RMPolicyBean;
import org.apache.sandesha2.storage.beans.RetransmitterBean;


/**
 * @author chamikara
 */

public class MessageRetransmissionAdjuster {

	public RetransmitterBean adjustRetransmittion (RetransmitterBean retransmitterBean) {
		String storedKey = (String) retransmitterBean.getKey();
		
		if (storedKey==null)
			return retransmitterBean;
		
		MessageContext messageContext = SandeshaUtil.getStoredMessageContext(storedKey);
		
		if (messageContext.getSystemContext()==null)
			return retransmitterBean;
		
		RMPolicyBean policyBean = (RMPolicyBean) messageContext.getProperty(Constants.WSP.RM_POLICY_BEAN);
		if (policyBean==null){
			return retransmitterBean;
		}
		
		long oldRetransmissionTime = retransmitterBean.getTimeToSend();
		
		retransmitterBean.setSentCount(retransmitterBean.getSentCount()+1);
		adjustNextRetransmissionTime (retransmitterBean,policyBean);
		
		if (retransmitterBean.getSentCount()>=Constants.MAXIMUM_RETRANSMISSION_ATTEMPTS)
			stopRetransmission (retransmitterBean);
		
		return retransmitterBean;
	}
	
	private RetransmitterBean adjustNextRetransmissionTime (RetransmitterBean retransmitterBean,RMPolicyBean policyBean) {
		
		long lastSentTime = retransmitterBean.getTimeToSend();
		
		int count = retransmitterBean.getSentCount();
		
		long baseInterval = policyBean.getRetransmissionInterval();
		
		long timeToSendNext;
		if (policyBean.isExponentialBackoff()) {
			long newInterval = generateNextExponentialBackedoffDifference (count,baseInterval);
			retransmitterBean.setTimeToSend(lastSentTime+newInterval);
		}
		
		return retransmitterBean;
	}
	
	private void stopRetransmission (RetransmitterBean bean) {
		bean.setReSend(false);
	}
	
	
	//TODO: Have to change this to be plugable
	private long generateNextExponentialBackedoffDifference(int count,long initialInterval) {
		long interval = initialInterval;
		for (int i=1;i<=count;i++){
			interval = interval*2;
		}
		
		return interval;
	}
	
	
}
