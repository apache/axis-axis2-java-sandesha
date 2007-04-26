/*
 * Copyright 1999-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *  
 */

package org.apache.sandesha2.msgprocessors;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.polling.PollingManager;
import org.apache.sandesha2.security.SecurityManager;
import org.apache.sandesha2.security.SecurityToken;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.beanmanagers.RMDBeanMgr;
import org.apache.sandesha2.storage.beans.RMDBean;
import org.apache.sandesha2.storage.beans.RMSBean;
import org.apache.sandesha2.util.SandeshaUtil;
import org.apache.sandesha2.util.TerminateManager;
import org.apache.sandesha2.wsrm.TerminateSequenceResponse;

/**
 * To process terminate sequence response messages.
 */
public class TerminateSeqResponseMsgProcessor implements MsgProcessor {

	private static final Log log = LogFactory.getLog(TerminateSeqResponseMsgProcessor.class);
	
	public boolean processInMessage(RMMsgContext terminateResRMMsg)
			throws AxisFault { 
		if(log.isDebugEnabled()) log.debug("Enter: TerminateSeqResponseMsgProcessor::processInMessage");
		
		MessageContext msgContext = terminateResRMMsg.getMessageContext();
		ConfigurationContext context = terminateResRMMsg.getConfigurationContext();
		
		StorageManager storageManager = SandeshaUtil.getSandeshaStorageManager(context,context.getAxisConfiguration());
		
		TerminateSequenceResponse tsResponse = (TerminateSequenceResponse)
		  terminateResRMMsg.getMessagePart(Sandesha2Constants.MessageParts.TERMINATE_SEQ_RESPONSE);
		
		String sequenceId = tsResponse.getIdentifier().getIdentifier();
		RMSBean rmsBean = SandeshaUtil.getRMSBeanFromSequenceId(storageManager, sequenceId);

		// Check that the sender of this TerminateSequence holds the correct token
		if(rmsBean != null && rmsBean.getSecurityTokenData() != null) {
			SecurityManager secManager = SandeshaUtil.getSecurityManager(context);
			OMElement body = terminateResRMMsg.getSOAPEnvelope().getBody();
			SecurityToken token = secManager.recoverSecurityToken(rmsBean.getSecurityTokenData());
			secManager.checkProofOfPossession(token, body, msgContext);
		}

		msgContext.setProperty(Sandesha2Constants.MessageContextProperties.INTERNAL_SEQUENCE_ID,rmsBean.getInternalSequenceID());

		//shedulling a polling request for the response side.
		if (rmsBean.getOfferedSequence()!=null) {
			RMDBeanMgr rMDBeanMgr = storageManager.getRMDBeanMgr();
			RMDBean rMDBean = rMDBeanMgr.retrieve(sequenceId);
			
			if (rMDBean!=null && rMDBean.isPollingMode()) {
				PollingManager manager = storageManager.getPollingManager();
				if(manager != null) manager.schedulePollingRequest(rMDBean.getSequenceID(), false);
			}
		}

		TerminateManager.terminateSendingSide (rmsBean, storageManager);
		
		// Stop this message travelling further through the Axis runtime
		terminateResRMMsg.pause();

		if(log.isDebugEnabled()) log.debug("Exit: TerminateSeqResponseMsgProcessor::processInMessage " + Boolean.TRUE);
		return true;
  }

	public boolean processOutMessage(RMMsgContext rmMsgCtx) {
		if(log.isDebugEnabled()) log.debug("Enter: TerminateSeqResponseMsgProcessor::processOutMessage");
		if(log.isDebugEnabled()) log.debug("Exit: TerminateSeqResponseMsgProcessor::processOutMessage " + Boolean.FALSE);
		return false;
	}
}