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

package org.apache.sandesha2.handlers;

import javax.xml.namespace.QName;

import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.ListenerManager;
import org.apache.axis2.context.AbstractContext;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.OperationContextFactory;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.ParameterImpl;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.axis2.soap.SOAPBody;
import org.apache.axis2.soap.SOAPEnvelope;
import org.apache.axis2.soap.SOAPFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.Constants;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.policy.RMPolicyBean;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.beanmanagers.CreateSeqBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.SenderBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.SequencePropertyBeanMgr;
import org.apache.sandesha2.storage.beans.CreateSeqBean;
import org.apache.sandesha2.storage.beans.SenderBean;
import org.apache.sandesha2.storage.beans.SequencePropertyBean;
import org.apache.sandesha2.util.MsgInitializer;
import org.apache.sandesha2.util.RMMsgCreator;
import org.apache.sandesha2.util.RMPolicyManager;
import org.apache.sandesha2.util.SOAPAbstractFactory;
import org.apache.sandesha2.util.SandeshaUtil;
import org.apache.sandesha2.util.SequenceManager;
import org.apache.sandesha2.wsrm.AckRequested;
import org.apache.sandesha2.wsrm.CreateSequence;
import org.apache.sandesha2.wsrm.Identifier;
import org.apache.sandesha2.wsrm.LastMessage;
import org.apache.sandesha2.wsrm.MessageNumber;
import org.apache.sandesha2.wsrm.Sequence;
import org.apache.sandesha2.wsrm.SequenceOffer;
import org.apache.wsdl.WSDLConstants;

/**
 * This is invoked in the outFlow of an RM endpoint
 * 
 * @author Chamikara Jayalath <chamikaramj@gmail.com>
 */

public class SandeshaOutHandler extends AbstractHandler {

	protected Log log = LogFactory.getLog(SandeshaOutHandler.class.getName());

	public void invoke(MessageContext msgCtx) throws AxisFault {

		//try {

		ConfigurationContext context = msgCtx.getSystemContext();
		if (context == null)
			throw new AxisFault("ConfigurationContext is null");

		AxisService axisService = msgCtx.getAxisService();
		if (axisService == null)
			throw new AxisFault("AxisService is null");

		if (!msgCtx.isServerSide()) {
			//getting rm message
			RMMsgContext rmMsgCtx = null;

			rmMsgCtx = MsgInitializer.initializeMessage(msgCtx);

			if (rmMsgCtx.getMessageType() == Constants.MessageTypes.UNKNOWN) {
				Parameter param = new ParameterImpl(Constants.RM_ENABLE_KEY,
						"true");
				axisService.addParameter(param);
			}
		}

		//getting rm message
		RMMsgContext rmMsgCtx = MsgInitializer.initializeMessage(msgCtx);

		Parameter keyParam = axisService.getParameter(Constants.RM_ENABLE_KEY);
		Object keyValue = null;
		if (keyParam != null)
			keyValue = keyParam.getValue();

		if (keyValue == null || !keyValue.equals("true")) {
			//RM is not enabled for the service. Quiting SandeshaOutHandler
			return;
		}

		String DONE = (String) msgCtx
				.getProperty(Constants.APPLICATION_PROCESSING_DONE);
		if (null != DONE && "true".equals(DONE))
			return;

		msgCtx.setProperty(Constants.APPLICATION_PROCESSING_DONE, "true");

		ServiceContext serviceContext = msgCtx.getServiceContext();
		Object debug = null;
		if (serviceContext != null) {
			debug = serviceContext.getProperty(Constants.SANDESHA_DEBUG_MODE);
			if (debug != null && "on".equals(debug)) {
				System.out.println("DEBUG: SandeshaOutHandler got a '"
						+ SandeshaUtil.getMessageTypeString(rmMsgCtx
								.getMessageType()) + "' message.");
			}
		}

		//TODO recheck
		//continue only if an possible application message
		if (!(rmMsgCtx.getMessageType() == Constants.MessageTypes.UNKNOWN)) {
			return;
		}

		//Strating the sender.
		SandeshaUtil.startSenderIfStopped(context);

		//Adding the policy bean
		RMPolicyBean policyBean = RMPolicyManager.getPolicyBean(rmMsgCtx);
		rmMsgCtx.setProperty(Constants.WSP.RM_POLICY_BEAN, policyBean);

		StorageManager storageManager = SandeshaUtil
				.getSandeshaStorageManager(context);

		CreateSeqBeanMgr createSeqMgr = storageManager.getCreateSeqBeanMgr();
		SequencePropertyBeanMgr seqPropMgr = storageManager
				.getSequencePropretyBeanMgr();

		boolean serverSide = msgCtx.isServerSide();

		//setting message Id if null
		if (msgCtx.getMessageID() == null) {
			msgCtx.setMessageID(SandeshaUtil.getUUID());
		}
		//initial work
		//find internal sequence id
		String internalSequenceId = null;

		//Temp sequence id is the one used to refer to the sequence (since
		//actual sequence id is not available when first msg arrives)
		//server side - sequenceId if the incoming sequence
		//client side - xxxxxxxxx
		if (serverSide) {
			//getting the request message and rmMessage.
			MessageContext reqMsgCtx = msgCtx.getOperationContext()
					.getMessageContext(WSDLConstants.MESSAGE_LABEL_IN_VALUE);

			RMMsgContext requestRMMsgCtx = MsgInitializer
					.initializeMessage(reqMsgCtx);

			Sequence reqSequence = (Sequence) requestRMMsgCtx
					.getMessagePart(Constants.MessageParts.SEQUENCE);
			if (reqSequence == null)
				throw new SandeshaException("Sequence part is null");

			String incomingSeqId = reqSequence.getIdentifier().getIdentifier();
			if (incomingSeqId == null || incomingSeqId == "")
				throw new SandeshaException("Invalid seqence Id");

			internalSequenceId = incomingSeqId;

		} else {
			//set the internal sequence id for the client side.
			EndpointReference toEPR = msgCtx.getTo();
			if (toEPR == null || toEPR.getAddress() == null
					|| "".equals(toEPR.getAddress()))
				throw new AxisFault(
						"TO End Point Reference is not set correctly. This is a must for the sandesha client side.");

			internalSequenceId = toEPR.getAddress();
			String sequenceKey = (String) context
					.getProperty(Constants.SEQUENCE_KEY);
			if (sequenceKey != null)
				internalSequenceId = internalSequenceId + sequenceKey;

		}

		//check if the fist message

		long messageNumber = getNextMsgNo(context, internalSequenceId);

		boolean sendCreateSequence = false;

		SequencePropertyBean outSeqBean = seqPropMgr.retrieve(internalSequenceId,
				Constants.SequenceProperties.OUT_SEQUENCE_ID);

		if (messageNumber == 1) {
			if (outSeqBean == null) {
				sendCreateSequence = true;
			}

			//			SandeshaDynamicProperties dynamicProperties =
			// SandeshaUtil.getDynamicProperties();
			//			if (msgCtx.isSOAP11()) {
			//				dynamicProperties.setSOAPVersionURI(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
			//			}else {
			//				dynamicProperties.setSOAPVersionURI(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI);
			//			}

			//TODO: set the policy bean
			//dynamicProperties.setPolicyBean();
		}

		//if fist message - setup the sequence for the client side
		if (!serverSide && sendCreateSequence) {
			SequenceManager.setupNewClientSequence(msgCtx, internalSequenceId);
		}

		//if first message - add create sequence
		if (sendCreateSequence) {

			SequencePropertyBean responseCreateSeqAdded = seqPropMgr.retrieve(
					internalSequenceId,
					Constants.SequenceProperties.OUT_CREATE_SEQUENCE_SENT);

			if (responseCreateSeqAdded == null) {
				responseCreateSeqAdded = new SequencePropertyBean(
						internalSequenceId,
						Constants.SequenceProperties.OUT_CREATE_SEQUENCE_SENT,
						"true");
				seqPropMgr.insert(responseCreateSeqAdded);

				String acksTo = null;
				if (serviceContext != null) {
					acksTo = (String) serviceContext
							.getProperty(Constants.AcksTo);
				}

				if (acksTo == null)
					acksTo = Constants.WSA.NS_URI_ANONYMOUS;

				//If acksTo is not anonymous. Start the listner TODO: verify
				if (!Constants.WSA.NS_URI_ANONYMOUS.equals(acksTo)
						&& !serverSide) {
					String transportIn = (String) context
							.getProperty(MessageContext.TRANSPORT_IN);
					if (transportIn == null)
						transportIn = org.apache.axis2.Constants.TRANSPORT_HTTP;
					ListenerManager.makeSureStarted(transportIn, context);
				} else if (acksTo == null && serverSide) {
					String incomingSequencId = SandeshaUtil
							.getServerSideIncomingSeqIdFromInternalSeqId(internalSequenceId);
					SequencePropertyBean bean = seqPropMgr.retrieve(
							incomingSequencId,
							Constants.SequenceProperties.REPLY_TO_EPR);
					if (bean != null) {
						EndpointReference acksToEPR = (EndpointReference) bean
								.getValue();
						if (acksToEPR != null)
							acksTo = (String) acksToEPR.getAddress();
					}
				} else if (Constants.WSA.NS_URI_ANONYMOUS.equals(acksTo)) {
					//set transport in.
					Object trIn = msgCtx
							.getProperty(MessageContext.TRANSPORT_IN);
					if (trIn == null) {

					}
				}

				addCreateSequenceMessage(rmMsgCtx, internalSequenceId, acksTo);

			}
		}

		//do response processing

		SOAPEnvelope env = rmMsgCtx.getSOAPEnvelope();
		if (env == null) {
			SOAPEnvelope envelope = SOAPAbstractFactory.getSOAPFactory(
					SandeshaUtil.getSOAPVersion(env)).getDefaultEnvelope();
			rmMsgCtx.setSOAPEnvelop(envelope);
		}

		SOAPBody soapBody = rmMsgCtx.getSOAPEnvelope().getBody();
		if (soapBody == null)
			throw new SandeshaException(
					"Invalid SOAP message. Body is not present");

		//TODO - Is this a correct way to find out validity of app.
		// messages.
		boolean validAppMessage = false;
		if (soapBody.getChildElements().hasNext())
			validAppMessage = true;

		if (validAppMessage) {

			//valid response

			//Changing message Id.
			//TODO remove this when Axis2 start sending uuids as uuid:xxxx
			String messageId1 = SandeshaUtil.getUUID();
			if (rmMsgCtx.getMessageId() == null) {
				rmMsgCtx.setMessageId(messageId1);
			}

			if (serverSide) {

				//processing the response
				processResponseMessage(rmMsgCtx, internalSequenceId,
						messageNumber);

				MessageContext reqMsgCtx = msgCtx
						.getOperationContext()
						.getMessageContext(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
				RMMsgContext requestRMMsgCtx = MsgInitializer
						.initializeMessage(reqMsgCtx);

				//let the request end with 202 if a ack has not been
				// written in the incoming thread.
				if (reqMsgCtx.getProperty(Constants.ACK_WRITTEN) == null
						|| !"true".equals(reqMsgCtx
								.getProperty(Constants.ACK_WRITTEN)))
					reqMsgCtx.getOperationContext().setProperty(
							org.apache.axis2.Constants.RESPONSE_WRITTEN,
							"false");
			} else {

				//setting reply to FIXME
				//msgCtx.setReplyTo(new EndpointReference
				// ("http://localhost:9070/somethingWorking"));

				//Setting WSA Action if null
				//TODO: Recheck weather this actions are correct
				EndpointReference toEPR = msgCtx.getTo();

				if (toEPR == null)
					throw new SandeshaException("To EPR is not found");

				String to = toEPR.getAddress();
				String operationName = msgCtx.getOperationContext()
						.getAxisOperation().getName().getLocalPart();

				if (msgCtx.getWSAAction() == null) {
					msgCtx.setWSAAction(to + "/" + operationName);
				}

				if (msgCtx.getSoapAction() == null) {
					msgCtx
							.setSoapAction("\"" + to + "/" + operationName
									+ "\"");
				}

				//processing the response
				processResponseMessage(rmMsgCtx, internalSequenceId, messageNumber);

			}

			//pausing the message
			msgCtx.setPausedTrue(getName());
		}

		//		}catch (Exception e) {
		//			e.getStackTrace();
		//			throw new AxisFault ("Sandesha got an exception. See logs for
		// details");
		//		}

	}

	public void addCreateSequenceMessage(RMMsgContext applicationRMMsg,
			String internalSequenceId, String acksTo) throws SandeshaException {

		MessageContext applicationMsg = applicationRMMsg.getMessageContext();
		if (applicationMsg == null)
			throw new SandeshaException("Message context is null");
		RMMsgContext createSeqRMMessage = RMMsgCreator.createCreateSeqMsg(
				applicationRMMsg, internalSequenceId, acksTo);
		CreateSequence createSequencePart = (CreateSequence) createSeqRMMessage
				.getMessagePart(Constants.MessageParts.CREATE_SEQ);
		if (createSequencePart == null)
			throw new SandeshaException(
					"Create Sequence part is null for a CreateSequence message");

		SequenceOffer offer = createSequencePart.getSequenceOffer();
		if (offer != null) {
			//Offer processing
			String offeredSequenceId = offer.getIdentifer().getIdentifier();
			SequencePropertyBean msgsBean = new SequencePropertyBean();
			msgsBean.setSequenceId(offeredSequenceId);
			msgsBean.setName(Constants.SequenceProperties.RECEIVED_MESSAGES);
			msgsBean.setValue("");

			SequencePropertyBean offeredSequenceBean = new SequencePropertyBean();
			offeredSequenceBean
					.setName(Constants.SequenceProperties.OFFERED_SEQUENCE);
			offeredSequenceBean.setSequenceId(internalSequenceId);
			offeredSequenceBean.setValue(offeredSequenceId);

			StorageManager storageManager = SandeshaUtil
					.getSandeshaStorageManager(applicationMsg
							.getSystemContext());

			SequencePropertyBeanMgr seqPropMgr = storageManager
					.getSequencePropretyBeanMgr();

			seqPropMgr.insert(msgsBean);
			seqPropMgr.insert(offeredSequenceBean);
		}

		MessageContext createSeqMsg = createSeqRMMessage.getMessageContext();

		//TODO remove below
		//createSeqMsg.setReplyTo(new EndpointReference
		// ("http://localhost:9070/somethingWorking"));

		createSeqMsg.setRelatesTo(null); //create seq msg does not relateTo
		// anything
		AbstractContext context = applicationRMMsg.getContext();
		if (context == null)
			throw new SandeshaException("Context is null");

		StorageManager storageManager = SandeshaUtil
				.getSandeshaStorageManager(applicationMsg.getSystemContext());
		CreateSeqBeanMgr createSeqMgr = storageManager.getCreateSeqBeanMgr();

		CreateSeqBean createSeqBean = new CreateSeqBean(internalSequenceId,
				createSeqMsg.getMessageID(), null);
		createSeqMgr.insert(createSeqBean);

		if (createSeqMsg.getReplyTo() == null)
			createSeqMsg.setReplyTo(new EndpointReference(
					Constants.WSA.NS_URI_ANONYMOUS));

		SenderBeanMgr retransmitterMgr = storageManager
				.getRetransmitterBeanMgr();

		String key = SandeshaUtil.storeMessageContext(createSeqRMMessage
				.getMessageContext());
		SenderBean createSeqEntry = new SenderBean();
		createSeqEntry.setKey(key);
		createSeqEntry.setTimeToSend(System.currentTimeMillis());
		createSeqEntry.setMessageId(createSeqRMMessage.getMessageId());
		createSeqEntry.setSend(true);
		retransmitterMgr.insert(createSeqEntry);

	}

	private void processResponseMessage(RMMsgContext rmMsg,
			String internalSequenceId, long messageNumber) throws SandeshaException {

		MessageContext msg = rmMsg.getMessageContext();

		SOAPFactory factory = SOAPAbstractFactory.getSOAPFactory(SandeshaUtil
				.getSOAPVersion(rmMsg.getSOAPEnvelope()));

		if (rmMsg == null)
			throw new SandeshaException("Message or reques message is null");

		AbstractContext context = rmMsg.getContext();
		if (context == null)
			throw new SandeshaException("Context is null");

		StorageManager storageManager = SandeshaUtil
				.getSandeshaStorageManager(msg.getSystemContext());
		SequencePropertyBeanMgr sequencePropertyMgr = storageManager
				.getSequencePropretyBeanMgr();

		SenderBeanMgr retransmitterMgr = storageManager
				.getRetransmitterBeanMgr();

		SequencePropertyBean toBean = sequencePropertyMgr.retrieve(
				internalSequenceId, Constants.SequenceProperties.TO_EPR);
		SequencePropertyBean replyToBean = sequencePropertyMgr.retrieve(
				internalSequenceId, Constants.SequenceProperties.REPLY_TO_EPR);

		//again - looks weird in the client side - but consistent
		SequencePropertyBean outSequenceBean = sequencePropertyMgr.retrieve(
				internalSequenceId, Constants.SequenceProperties.OUT_SEQUENCE_ID);

		if (toBean == null)
			throw new SandeshaException("To is null");
		//		if (replyToBean == null)
		//			throw new SandeshaException("Replyto is null");

		EndpointReference toEPR = (EndpointReference) toBean.getValue();
		EndpointReference replyToEPR = null;

		if (replyToBean != null) {
			replyToEPR = (EndpointReference) replyToBean.getValue();
		}

		if (toEPR == null || toEPR.getAddress() == null
				|| toEPR.getAddress() == "")
			throw new SandeshaException("To Property has an invalid value");

		//		if (replyToEPR == null || replyToEPR.getAddress() == null
		//				|| replyToEPR.getAddress() == "")
		//			throw new SandeshaException("ReplyTo is not set correctly");

		//Setting wsa:To to the replyTo value of the respective request message
		//(instead of the replyTo of the CreateSequenceMessage)

		String newToStr = null;

		if (msg.isServerSide()) {
			try {
				MessageContext requestMsg = msg.getOperationContext()
						.getMessageContext(
								OperationContextFactory.MESSAGE_LABEL_IN_VALUE);
				if (requestMsg != null) {
					newToStr = requestMsg.getReplyTo().getAddress();
				}
			} catch (AxisFault e) {
				throw new SandeshaException(e.getMessage());
			}
		}

		if (newToStr != null)
			rmMsg.setTo(new EndpointReference(newToStr));
		else
			rmMsg.setTo(toEPR);

		if (replyToEPR != null)
			rmMsg.setReplyTo(replyToEPR);

		Sequence sequence = new Sequence(factory);

		MessageNumber msgNumber = new MessageNumber(factory);
		msgNumber.setMessageNumber(messageNumber);
		sequence.setMessageNumber(msgNumber);

		boolean lastMessage = false;
		//setting last message
		if (msg.isServerSide()) {
			//server side
			String incomingSeqId = internalSequenceId;
			MessageContext requestMsg = null;

			try {
				requestMsg = msg.getOperationContext().getMessageContext(
						WSDLConstants.MESSAGE_LABEL_IN_VALUE);
			} catch (AxisFault e) {
				throw new SandeshaException(e.getMessage());
			}

			RMMsgContext reqRMMsgCtx = MsgInitializer
					.initializeMessage(requestMsg);
			Sequence requestSequence = (Sequence) reqRMMsgCtx
					.getMessagePart(Constants.MessageParts.SEQUENCE);
			if (requestSequence == null)
				throw new SandeshaException("Request Sequence is null");

			if (requestSequence.getLastMessage() != null) {
				lastMessage = true;
				//FIXME - This fails if request last message has more than one
				// responses.
				sequence.setLastMessage(new LastMessage(factory));

				//saving the last message no.
				SequencePropertyBean lastOutMsgBean = new SequencePropertyBean(
						internalSequenceId,
						Constants.SequenceProperties.LAST_OUT_MESSAGE,
						new Long(messageNumber));
				sequencePropertyMgr.insert(lastOutMsgBean);
			}

		} else {
			//client side

			ServiceContext serviceContext = msg.getServiceContext();
			if (serviceContext != null) {
				Object obj = serviceContext.getProperty(Constants.LAST_MESSAGE);
				if (obj != null && "true".equals(obj)) {
					lastMessage = true;
					sequence.setLastMessage(new LastMessage(factory));
					//saving the last message no.
					SequencePropertyBean lastOutMsgBean = new SequencePropertyBean(
							internalSequenceId,
							Constants.SequenceProperties.LAST_OUT_MESSAGE,
							new Long(messageNumber));
					sequencePropertyMgr.insert(lastOutMsgBean);
				}
			}
		}

		AckRequested ackRequested = null;

		//TODO do this based on policies.
		boolean addAckRequested = false;
		if (!lastMessage)
			addAckRequested = true;

		//setting the Sequnece id.
		//Set send = true/false depending on the availability of the out
		// sequence id.
		String identifierStr = null;
		if (outSequenceBean == null || outSequenceBean.getValue() == null) {
			identifierStr = Constants.TEMP_SEQUENCE_ID;
			//			identifier.setIndentifer(Constants.INTERNAL_SEQUENCE_ID);
			//			sequence.setIdentifier(identifier);

		} else {
			identifierStr = (String) outSequenceBean.getValue();
			//identifier.setIndentifer((String) outSequenceBean.getValue());
		}
		Identifier id1 = new Identifier(factory);
		id1.setIndentifer(identifierStr);
		sequence.setIdentifier(id1);
		rmMsg.setMessagePart(Constants.MessageParts.SEQUENCE, sequence);

		if (addAckRequested) {
			ackRequested = new AckRequested(factory);
			Identifier id2 = new Identifier(factory);
			id2.setIndentifer(identifierStr);
			ackRequested.setIdentifier(id2);
			rmMsg.setMessagePart(Constants.MessageParts.ACK_REQUEST,
					ackRequested);
		}

		try {
			rmMsg.addSOAPEnvelope();
		} catch (AxisFault e1) {
			throw new SandeshaException(e1.getMessage());
		}

		//		//send the message through sender only in the server case.
		//		//in the client case use the normal flow.
		//		if (msg.isServerSide()) {
		//Retransmitter bean entry for the application message
		SenderBean appMsgEntry = new SenderBean();
		String key = SandeshaUtil
				.storeMessageContext(rmMsg.getMessageContext());
		appMsgEntry.setKey(key);
		appMsgEntry.setTimeToSend(System.currentTimeMillis());
		appMsgEntry.setMessageId(rmMsg.getMessageId());
		appMsgEntry.setMessageNumber(messageNumber);
		if (outSequenceBean == null || outSequenceBean.getValue() == null) {
			appMsgEntry.setSend(false);
		} else {
			appMsgEntry.setSend(true);

		}
		appMsgEntry.setInternalSequenceId(internalSequenceId);
		retransmitterMgr.insert(appMsgEntry);
		//		}
	}

	private long getNextMsgNo(ConfigurationContext context,
			String internalSequenceId) throws SandeshaException {
		//FIXME set a correct message number.

		StorageManager storageManager = SandeshaUtil
				.getSandeshaStorageManager(context);

		SequencePropertyBeanMgr seqPropMgr = storageManager
				.getSequencePropretyBeanMgr();
		SequencePropertyBean nextMsgNoBean = seqPropMgr.retrieve(
				internalSequenceId,
				Constants.SequenceProperties.NEXT_MESSAGE_NUMBER);
		long nextMsgNo = 1;
		boolean update = false;
		if (nextMsgNoBean != null) {
			update = true;
			Long nextMsgNoLng = (Long) nextMsgNoBean.getValue();
			nextMsgNo = nextMsgNoLng.longValue();
		} else {
			nextMsgNoBean = new SequencePropertyBean();
			nextMsgNoBean.setSequenceId(internalSequenceId);
			nextMsgNoBean
					.setName(Constants.SequenceProperties.NEXT_MESSAGE_NUMBER);
		}

		nextMsgNoBean.setValue(new Long(nextMsgNo + 1));
		if (update)
			seqPropMgr.update(nextMsgNoBean);
		else
			seqPropMgr.insert(nextMsgNoBean);

		return nextMsgNo;
	}

	public QName getName() {
		return new QName(Constants.OUT_HANDLER_NAME);
	}
}