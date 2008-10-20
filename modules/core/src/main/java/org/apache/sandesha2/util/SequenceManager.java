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
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.description.ClientUtils;
import org.apache.axis2.description.WSDL2Constants;
import org.apache.axis2.util.JavaUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.client.SandeshaClient;
import org.apache.sandesha2.client.SandeshaClientConstants;
import org.apache.sandesha2.client.SandeshaListener;
import org.apache.sandesha2.client.SequenceReport;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;
import org.apache.sandesha2.policy.SandeshaPolicyBean;
import org.apache.sandesha2.security.SecurityManager;
import org.apache.sandesha2.security.SecurityToken;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.beans.RMDBean;
import org.apache.sandesha2.storage.beans.RMSBean;
import org.apache.sandesha2.workers.SequenceEntry;
import org.apache.sandesha2.wsrm.CreateSequence;

/**
 * This is used to set up a new sequence, both at the sending side and the
 * receiving side.
 */

public class SequenceManager {

	private static Log log = LogFactory.getLog(SequenceManager.class);

	/**
	 * Set up a new inbound sequence, triggered by the arrival of a create sequence message. As this
	 * is an inbound sequence, the sequencePropertyKey is the sequenceId.
	 */
	public static RMDBean setupNewSequence(RMMsgContext createSequenceMsg, StorageManager storageManager, SecurityManager securityManager, SecurityToken token)
			throws AxisFault {
		if (log.isDebugEnabled())
			log.debug("Enter: SequenceManager::setupNewSequence");
		
		
		// Generate the new RMD Bean
		RMDBean rmdBean = new RMDBean();

		EndpointReference to = createSequenceMsg.getTo();
		if (to == null) {
			String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.toEPRNotValid, null);
			log.debug(message);
			throw new AxisFault(message);
		}

		EndpointReference replyTo = createSequenceMsg.getReplyTo();

		CreateSequence createSequence = createSequenceMsg.getCreateSequence();
		if (createSequence == null) {
			String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.createSeqEntryNotFound);
			log.debug(message);
			throw new AxisFault(message);
		}

		EndpointReference acksTo = createSequence.getAcksTo().getEPR();

		if (acksTo == null) {
			log.error(SandeshaMessageHelper.getMessage(SandeshaMessageKeys.acksToInvalid, ""));
			FaultManager.makeCreateSequenceRefusedFault(createSequenceMsg, SandeshaMessageHelper.getMessage(SandeshaMessageKeys.noAcksToPartInCreateSequence), new Exception(), null);
			return null;
		} else if (acksTo.getAddress().equals(AddressingConstants.Final.WSA_NONE_URI)){
			log.error(SandeshaMessageHelper.getMessage(SandeshaMessageKeys.acksToInvalid, acksTo.getAddress()));
			FaultManager.makeCreateSequenceRefusedFault(createSequenceMsg, "AcksTo can not be " + AddressingConstants.Final.WSA_NONE_URI, new Exception(), null);
			return null;
		}

		MessageContext createSeqContext = createSequenceMsg.getMessageContext();
		
		// If this create is the result of a MakeConnection, then we must have a related
		// outbound sequence.
		SequenceEntry entry = (SequenceEntry) createSeqContext.getProperty(Sandesha2Constants.MessageContextProperties.MAKECONNECTION_ENTRY);
		if(log.isDebugEnabled()) log.debug("This message is associated with sequence entry: " + entry);
		if(entry != null && entry.isRmSource()) {
			rmdBean.setOutboundInternalSequence(entry.getSequenceId());
		}

		rmdBean.setServerCompletedMessages(new RangeString());
		
		rmdBean.setReplyToEndpointReference(to);
		rmdBean.setAcksToEndpointReference(acksTo);

		// If no replyTo value. Send responses as sync.
		if (replyTo != null)
			rmdBean.setToEndpointReference(replyTo);

		// Store the security token alongside the sequence
		if(token != null) {
			String tokenData = securityManager.getTokenRecoveryData(token);
			rmdBean.setSecurityTokenData(tokenData);
		}		
		
		rmdBean.setSequenceID(SandeshaUtil.getUUID());
		rmdBean.setNextMsgNoToProcess(1);
		
		rmdBean.setToAddress(to.getAddress());
		
		// If this sequence has a 'To' address that is anonymous then we must have got the
		// message as a response to a poll. We need to make sure that we keep polling until
		// the sequence is closed.
		if(to.hasAnonymousAddress()) {
			String newKey = SandeshaUtil.getUUID();
			rmdBean.setPollingMode(true);
			rmdBean.setReferenceMessageKey(newKey);
			storageManager.storeMessageContext(newKey, createSeqContext);
		}

		String messageRMNamespace = createSequence.getNamespaceValue();

		String specVersion = null;
		if (Sandesha2Constants.SPEC_2005_02.NS_URI.equals(messageRMNamespace)) {
			specVersion = Sandesha2Constants.SPEC_VERSIONS.v1_0;
		} else if (Sandesha2Constants.SPEC_2007_02.NS_URI.equals(messageRMNamespace)) {
			specVersion = Sandesha2Constants.SPEC_VERSIONS.v1_1;
		} else {
			throw new SandeshaException(SandeshaMessageHelper.getMessage(SandeshaMessageKeys.cannotDecideRMVersion));
		}

		rmdBean.setRMVersion(specVersion);
		rmdBean.setLastActivatedTime(System.currentTimeMillis());

		storageManager.getRMDBeanMgr().insert(rmdBean);

		// TODO get the SOAP version from the create seq message.

		if (log.isDebugEnabled())
			log.debug("Exit: SequenceManager::setupNewSequence, " + rmdBean);
		return rmdBean;
	}

	public void removeSequence(String sequence) {

	}

	public static RMSBean setupNewClientSequence(MessageContext firstAplicationMsgCtx,
			String internalSequenceId, StorageManager storageManager) throws SandeshaException {
		if (log.isDebugEnabled())
			log.debug("Enter: SequenceManager::setupNewClientSequence");
		
		RMSBean rmsBean = new RMSBean();
		rmsBean.setInternalSequenceID(internalSequenceId);

		// If we are server-side, we use the details from the inbound sequence to help set
		// up the reply sequence.
		String inboundSequence = null;
		RMDBean inboundBean = null;
		if(firstAplicationMsgCtx.isServerSide()) {
			inboundSequence = (String) firstAplicationMsgCtx.getProperty(Sandesha2Constants.MessageContextProperties.INBOUND_SEQUENCE_ID);
			if(inboundSequence != null) {
				inboundBean = SandeshaUtil.getRMDBeanFromSequenceId(storageManager, inboundSequence);
				if (log.isDebugEnabled())
					log.debug("SequenceManager:: server side app msg: inboundBean=" + inboundBean);
			}
		}
		
		// Finding the spec version
		String specVersion = getSpecVersion(firstAplicationMsgCtx, storageManager);
		rmsBean.setRMVersion(specVersion);

		// Set up the To EPR
		EndpointReference toEPR = firstAplicationMsgCtx.getTo();

		if (toEPR == null) {
			String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.toEPRNotValid, null);
			log.debug(message);
			throw new SandeshaException(message);
		}

		rmsBean.setToEndpointReference(toEPR);

		// Discover the correct acksTo and replyTo EPR for this RMSBean
		EndpointReference acksToEPR = null;
		EndpointReference replyToEPR = null;

		if (firstAplicationMsgCtx.isServerSide()) {
			// Server side, we want the replyTo and AcksTo EPRs to point into this server.
			// We can work that out by looking at the RMD bean that pulled the message in,
			// and copying its 'ReplyTo' address.
			EndpointReference strippedReplyToEpr = stripAddress(inboundBean.getReplyToEndpointReference());
			if(inboundBean != null && strippedReplyToEpr != null) {
				acksToEPR = strippedReplyToEpr;
				replyToEPR = strippedReplyToEpr;
		} else {
				String beanInfo = (inboundBean == null) ? "null" : inboundBean.toString();
				String message = SandeshaMessageHelper.getMessage(
						SandeshaMessageKeys.cannotChooseAcksTo, inboundSequence, beanInfo);
				SandeshaException e = new SandeshaException(message);
				if(log.isDebugEnabled()) log.debug("Throwing", e);
				throw e;
			}

		} else {
			replyToEPR = firstAplicationMsgCtx.getReplyTo();
			
			// If the replyTo is the none URI, we need to rewrite it as the
			// anon replyTo. Setting the value to null should have that effect.
			if(replyToEPR != null && replyToEPR.hasNoneAddress()) {
				replyToEPR = null;
			}

            // if this is an in only service invocation and if the user has set the
            // use seperate listner option then it should work as an dual channel model.
            if ((replyToEPR == null) && firstAplicationMsgCtx.getOptions().isUseSeparateListener() &&
                    firstAplicationMsgCtx.getAxisOperation().getMessageExchangePattern().equals(WSDL2Constants.MEP_URI_OUT_ONLY)){

                // first check whether the transport in is set or not
                TransportInDescription transportIn = firstAplicationMsgCtx.getTransportIn();
                if (transportIn == null) {
                    transportIn = firstAplicationMsgCtx.getOptions().getTransportIn();
                }

                //If use seperate listner is false then we have to use the annonymous end point.
                if ((transportIn == null) && firstAplicationMsgCtx.getOptions().isUseSeparateListener()) {
                    try {
                        transportIn = ClientUtils.inferInTransport(
                                firstAplicationMsgCtx.getConfigurationContext().getAxisConfiguration(),
                                firstAplicationMsgCtx.getOptions(),
                                firstAplicationMsgCtx);
                        replyToEPR = firstAplicationMsgCtx.getConfigurationContext().getListenerManager().getEPRforService(
                            firstAplicationMsgCtx.getAxisService().getName(),
                            firstAplicationMsgCtx.getAxisOperation().getName().getLocalPart(),
                            transportIn.getName());
                    } catch (AxisFault axisFault) {
                        throw new SandeshaException("Can not infer replyToEPR from the first message context ", axisFault);
                    }
                }
            }

            // For client-side sequences there are 3 options:
			// 1) An explict AcksTo, set via the client API
			// 2) The replyTo from the app message
			// 3) The anonymous URI (for which we can leave a null EPR)
			String acksTo = (String) firstAplicationMsgCtx.getProperty(SandeshaClientConstants.AcksTo);
			if (acksTo != null) {
				if (log.isDebugEnabled())
					log.debug("Using explicit AcksTo, addr=" + acksTo);
				acksToEPR = new EndpointReference(acksTo);
			} else if(replyToEPR != null) {
				if (log.isDebugEnabled())
					log.debug("Using replyTo EPR as AcksTo, addr=" + replyToEPR.getAddress());
				acksToEPR = replyToEPR;
			}
		}
		// In case either of the replyTo or AcksTo is anonymous, rewrite them using the AnonURI template
		//(this should be done only for RM 1.1)
		ConfigurationContext config = firstAplicationMsgCtx.getConfigurationContext();
		
		if (Sandesha2Constants.SPEC_VERSIONS.v1_1.equals(specVersion)) {
			replyToEPR = SandeshaUtil.rewriteEPR(rmsBean, replyToEPR, config);
			acksToEPR = SandeshaUtil.rewriteEPR(rmsBean, acksToEPR, config);
		}
		
		// Store both the acksTo and replyTo 
		if(replyToEPR != null) rmsBean.setReplyToEndpointReference(replyToEPR);
		if(acksToEPR  != null) rmsBean.setAcksToEndpointReference(acksToEPR);
		
		// New up the client completed message ranges list
		rmsBean.setClientCompletedMessages(new RangeString());

		// saving transportTo value;
		String transportTo = (String) firstAplicationMsgCtx.getProperty(Constants.Configuration.TRANSPORT_URL);
		if (transportTo != null) {
			rmsBean.setTransportTo(transportTo);
		}

		// Set the soap version use by this client
		rmsBean.setSoapVersion(SandeshaUtil.getSOAPVersion(firstAplicationMsgCtx.getEnvelope()));

		//setting the autoTermination property for the client side.
		if (!firstAplicationMsgCtx.isServerSide()) {
			Object avoidAutoTermination = firstAplicationMsgCtx.getProperty(SandeshaClientConstants.AVOID_AUTO_TERMINATION);
			if (avoidAutoTermination!=null && JavaUtils.isTrueExplicitly(avoidAutoTermination))
				rmsBean.setAvoidAutoTermination(true);
		}
		// updating the last activated time.
		rmsBean.setLastActivatedTime(System.currentTimeMillis());
		
		if (log.isDebugEnabled())
			log.debug("Exit: SequenceManager::setupNewClientSequence " + rmsBean);
		return rmsBean;
	}

	public static boolean hasSequenceTimedOut(RMSBean rmsBean, String internalSequenceId, StorageManager storageManager)
			throws SandeshaException {

		SandeshaPolicyBean propertyBean = 
			SandeshaUtil.getPropertyBean(storageManager.getContext().getAxisConfiguration());

		if (propertyBean.getInactivityTimeoutInterval() <= 0)
			return false;

		boolean sequenceTimedOut = false;
		
		long lastActivatedTime = rmsBean.getLastActivatedTime();
		long timeNow = System.currentTimeMillis();
		if (lastActivatedTime > 0 && (lastActivatedTime + propertyBean.getInactivityTimeoutInterval() < timeNow))
			sequenceTimedOut = true;

		return sequenceTimedOut;
	}
	
	public static void finalizeTimedOutSequence(String internalSequenceID, MessageContext messageContext,
			StorageManager storageManager) throws SandeshaException {
		if (log.isDebugEnabled()) log.debug("Enter: SequenceManager::finalizeTimedOutSequence:" + internalSequenceID);
		
		ConfigurationContext configurationContext = null;
		if (messageContext == null)
			configurationContext = storageManager.getContext();
		else 
			configurationContext = messageContext.getConfigurationContext();			

		// Notify the clients of a timeout
		AxisFault fault = new AxisFault(
				SandeshaMessageHelper.getMessage(SandeshaMessageKeys.cannotSendMsgAsSequenceTimedout, internalSequenceID));
		// Notify any waiting clients that the sequence has timeed out.
		FaultManager.notifyClientsOfFault(internalSequenceID, storageManager, configurationContext, fault);
		
		//try and send a terminate message
		try{
			RMSBean bean = new RMSBean();
			bean.setInternalSequenceID(internalSequenceID);
			storageManager.getRMSBeanMgr().findUnique(bean);
			if(bean!=null){
				TerminateManager.checkAndTerminate(configurationContext, storageManager, bean);
			}			
		}
		catch(Exception e){
			//log this error but continue to timeout sequence
			if (log.isDebugEnabled()) log.debug("SequenceManager::finalizeTimedOutSequence:Error caught:" + e);
		}

		
		// Already an active transaction, so don't want a new one
		TerminateManager.timeOutSendingSideSequence(internalSequenceID, storageManager);
		

		if (messageContext != null) {
			SandeshaListener listener = (SandeshaListener) messageContext
					.getProperty(SandeshaClientConstants.SANDESHA_LISTENER);
			if (listener != null) {
				SequenceReport report = SandeshaClient.getOutgoingSequenceReport(internalSequenceID, configurationContext, false);
				listener.onTimeOut(report);
			}
		}
		if (log.isDebugEnabled()) log.debug("Exit: SequenceManager::finalizeTimedOutSequence");
	}

	public static String getSpecVersion(MessageContext applicationMessage, StorageManager storageManager)
	throws SandeshaException
	{
		String specVersion = null;
		if (applicationMessage.isServerSide()) {
			String inboundSequence = null;
			RMDBean inboundBean = null;
			if(applicationMessage.isServerSide()) {
				inboundSequence = (String) applicationMessage.getProperty(Sandesha2Constants.MessageContextProperties.INBOUND_SEQUENCE_ID);
				if(inboundSequence != null) {
					inboundBean = SandeshaUtil.getRMDBeanFromSequenceId(storageManager, inboundSequence);
				}
			}

			// in the server side, get the RM version from the request sequence.
			if(inboundBean == null || inboundBean.getRMVersion() == null) {
				String beanInfo = (inboundBean == null) ? "null" : inboundBean.toString();
				String message = SandeshaMessageHelper.getMessage(
						SandeshaMessageKeys.cannotChooseSpecLevel, inboundSequence, beanInfo );
				SandeshaException e = new SandeshaException(message);
				if(log.isDebugEnabled()) log.debug("Throwing", e);
				throw e;
			}

			specVersion = inboundBean.getRMVersion();
		} else {
			// in the client side, user will set the RM version.
			specVersion = (String) applicationMessage.getProperty(SandeshaClientConstants.RM_SPEC_VERSION);
			
			// If the spec version is null, look in the axis operation to see value has been set
			Parameter opLevel = applicationMessage.getAxisOperation().getParameter(SandeshaClientConstants.RM_SPEC_VERSION);
			if (specVersion == null && opLevel != null)	specVersion = (String) opLevel.getValue();						
		}

		if (specVersion == null)
			// TODO change the default to v1_1
			specVersion = SpecSpecificConstants.getDefaultSpecVersion(); 

		return specVersion;
	}
	
	/* becuase RM reuses the incoming EPRs.  Need to use only the address.
	 */
	private static EndpointReference stripAddress(EndpointReference eprIn){
		if(log.isDebugEnabled()) log.debug("stripAddress from EndpointReference : " + eprIn);
		EndpointReference epr = new EndpointReference(eprIn.getAddress());
		return epr;
/**		
		String schemaNs = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
		String securityNs = "http://schemas.xmlsoap.org/ws/2003/06/utility";
		
		Iterator it = eprOut.getAttributes().iterator();
		while(it.hasNext()){
			OMAttribute attribute = (OMAttribute)it.next();
			String ns = attribute.getNamespace().getNamespaceURI();
			String name = attribute.getLocalName();
			if((schemaNs.equals(ns) || securityNs.equals(ns)) && "Id".equals(name)){
				//delete attribute
				it.remove();
			} 
		}
		Iterator it2 = eprOut.getAddressAttributes().iterator();
		while(it2.hasNext()){
			OMAttribute attribute = (OMAttribute)it2.next();
			String ns = attribute.getNamespace().getNamespaceURI();
			String name = attribute.getLocalName();
			if((schemaNs.equals(ns) || securityNs.equals(ns)) && "Id".equals(name)){
				//delete attribute
				it2.remove();
			} 
		}
		
		return eprOut;
		**/
}

}
