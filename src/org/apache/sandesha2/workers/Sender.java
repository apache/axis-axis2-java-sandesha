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
package org.apache.sandesha2.workers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisOperationFactory;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.soap.SOAPEnvelope;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.AcknowledgementManager;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.TerminateManager;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.Transaction;
import org.apache.sandesha2.storage.beanmanagers.SenderBeanMgr;
import org.apache.sandesha2.storage.beans.SenderBean;
import org.apache.sandesha2.util.MessageRetransmissionAdjuster;
import org.apache.sandesha2.util.MsgInitializer;
import org.apache.sandesha2.util.PropertyManager;
import org.apache.sandesha2.util.SandeshaPropertyBean;
import org.apache.sandesha2.util.SandeshaUtil;
import org.apache.sandesha2.util.SequenceManager;
import org.apache.sandesha2.wsrm.Sequence;
import org.apache.sandesha2.wsrm.TerminateSequence;

/**
 * This is responsible for sending and re-sending messages of Sandesha2. This
 * represent a thread that keep running all the time. This keep looking at the
 * Sender table to find out any entries that should be sent.
 * 
 * @author Chamikara Jayalath <chamikaramj@gmail.com>
 */

public class Sender extends Thread {

	private boolean runSender = false;
	private boolean stopSenderAfterWork = false;
	private ArrayList workingSequences = new ArrayList();
	
	private ConfigurationContext context = null;
	
	Log log = LogFactory.getLog(getClass());

	public synchronized void stopSenderForTheSequence(String sequenceID) {
		workingSequences.remove(sequenceID);
		if (workingSequences.size()==0) {
			//stopSenderAfterWork = true;
		}
	}

	public synchronized boolean isSenderStarted() {
		return runSender;
	}

	public void run() {

		StorageManager storageManager = null;

		try {
			storageManager = SandeshaUtil.getSandeshaStorageManager(context);
		} catch (SandeshaException e2) {
			// TODO Auto-generated catch block
			log.debug ("ERROR: Could not start sender");
			e2.printStackTrace();
			return;
		}
		
		while (runSender) {
			
			
			try {
				if (context == null) {
					String message = "Can't continue the Sender. Context is null";
					log.debug(message);
					throw new SandeshaException(message);
				}

				Transaction pickMessagesToSendTransaction = storageManager.getTransaction(); //starting
																			   // a
																			   // new
																			   // transaction

				SenderBeanMgr mgr = storageManager.getRetransmitterBeanMgr();
				Collection coll = mgr.findMsgsToSend();
				if (coll.size()==0 && stopSenderAfterWork) {
					runSender = false;
					pickMessagesToSendTransaction.commit();
					continue;
				}
				
				pickMessagesToSendTransaction.commit();
				
				Iterator iter = coll.iterator();

				while (iter.hasNext()) {

					SenderBean bean = (SenderBean) iter.next();
					String key = (String) bean.getMessageContextRefKey();
					MessageContext msgCtx = storageManager.retrieveMessageContext(key,context);

					//sender will not send the message if following property is set and not true.
					//But it will set if it is not set (null)
					
					//This is used to make sure that the mesage get passed the Sandesha2TransportSender.
//					String qualifiedForSending = (String) msgCtx.getProperty(Sandesha2Constants.QUALIFIED_FOR_SENDING);
//					if (qualifiedForSending!=null && !qualifiedForSending.equals(Sandesha2Constants.VALUE_TRUE)) {
//						continue;
//					}
					
					try {

						if (msgCtx == null) {
							log.debug ("ERROR: Sender has an Unavailable Message entry");
							break;
						}
												
						RMMsgContext rmMsgCtx = MsgInitializer
								.initializeMessage(msgCtx);

						//skip sending if this message has been mentioned as a message not to send (within sandesha2.properties)
						ArrayList msgsNotToSend = PropertyManager.getInstance().getMessagesNotToSend();
						//SandeshaPropertyBean propertyBean = (SandeshaPropertyBean) messageContext.getParameter(Sandesha2Constants.SANDESHA2_POLICY_BEAN);
						
						if (msgsNotToSend!=null && msgsNotToSend.contains(new Integer (rmMsgCtx.getMessageType()))) {
							continue;
						}
						
						updateMessage(msgCtx);

						
						log.info("Sender is sending a '" + SandeshaUtil
									.getMessageTypeString(rmMsgCtx.getMessageType()) + "' message.");
						
						Transaction preSendTransaction = storageManager.getTransaction();

						int messageType = rmMsgCtx.getMessageType();
						
						if (messageType == Sandesha2Constants.MessageTypes.APPLICATION) {
							
							Sequence sequence = (Sequence) rmMsgCtx.getMessagePart(Sandesha2Constants.MessageParts.SEQUENCE);
							String sequenceID = sequence.getIdentifier().getIdentifier();
							//checking weather the sequence has been timed out.
							boolean sequenceTimedOut = SequenceManager.hasSequenceTimedOut (sequenceID, rmMsgCtx);;
							if (sequenceTimedOut) {
								//sequence has been timed out.
								//do time out processing.
								
								//TODO uncomment below line
								//TerminateManager.terminateSendingSide(context,sequenceID);
								
								String message = "Sequence timed out";
								log.debug(message);
								throw new SandeshaException (message);
							}
							
							//piggybacking if an ack if available for the same
							// sequence.
							AcknowledgementManager
									.piggybackAckIfPresent(rmMsgCtx);
						}
						

						
						preSendTransaction.commit();
						
						if (rmMsgCtx.getMessageType()==Sandesha2Constants.MessageTypes.TERMINATE_SEQ) {
							int i =1;
						}
						
						try {
							//every message should be resumed (pause==false) when sending
							boolean paused = msgCtx.isPaused();
							
							
							AxisEngine engine = new AxisEngine(msgCtx
									.getConfigurationContext());
							if (paused) {
								engine.resume(msgCtx);
							}else  {
								engine.send(msgCtx);
							}
							
						} catch (Exception e) {
							//Exception is sending. retry later
							String message = "Exception thrown in sending...";
							log.debug(message);
							log.debug(e.getMessage());

						} 
												
						Transaction postSendTransaction = storageManager.getTransaction();

						MessageRetransmissionAdjuster retransmitterAdjuster = new MessageRetransmissionAdjuster();

						if (rmMsgCtx.getMessageType() == Sandesha2Constants.MessageTypes.APPLICATION) {
							Sequence sequence = (Sequence) rmMsgCtx
									.getMessagePart(Sandesha2Constants.MessageParts.SEQUENCE);
							long messageNo = sequence.getMessageNumber()
									.getMessageNumber();
						}

						retransmitterAdjuster.adjustRetransmittion(bean,context);

							
						//update or delete only if the object is still present.
						SenderBean bean1 = mgr.retrieve(bean.getMessageID());
						if (bean1 != null) {
							if (bean.isReSend())
								mgr.update(bean);
							else
								mgr.delete(bean.getMessageID());
						}

						postSendTransaction.commit(); //commiting the current
												  // transaction

						Transaction processResponseTransaction =
						storageManager.getTransaction();
						if (!msgCtx.isServerSide())
							checkForSyncResponses(msgCtx);
												
						processResponseTransaction.commit();

						Transaction terminateCleaningTransaction = storageManager
								.getTransaction();
						if (rmMsgCtx.getMessageType() == Sandesha2Constants.MessageTypes.TERMINATE_SEQ) {
							//terminate sending side.
							TerminateSequence terminateSequence = (TerminateSequence) rmMsgCtx
									.getMessagePart(Sandesha2Constants.MessageParts.TERMINATE_SEQ);
							String sequenceID = terminateSequence
									.getIdentifier().getIdentifier();
							ConfigurationContext configContext = msgCtx
									.getConfigurationContext();

							TerminateManager.terminateSendingSide(
									configContext, sequenceID);
							
							//removing a entry from the Listener
							String transport = msgCtx.getTransportOut().getName().getLocalPart();
							
							
							//TODO complete below. Need a more eligent method which finishes the current message before ending.
							//ListenerManager.stop(configContext,transport);
						}

						terminateCleaningTransaction.commit();

					} catch (AxisFault e1) {
						e1.printStackTrace();
					} catch (Exception e3) {
						e3.printStackTrace();
					}
				}

			} catch (SandeshaException e) {
				e.printStackTrace();
				return;
			}

			try {
				Thread.sleep(Sandesha2Constants.SENDER_SLEEP_TIME);
			} catch (InterruptedException e1) {
				//e1.printStackTrace();
				log.debug("Sender was interupted...");
				log.debug(e1.getMessage());
				log.debug("End printing Interrupt...");
			}
		}

	}

	private boolean isResponseExpected(RMMsgContext rmMsgCtx) {
		boolean responseExpected = false;

		if (rmMsgCtx.getMessageType() == Sandesha2Constants.MessageTypes.CREATE_SEQ) {
			responseExpected = true;
		}
		if (rmMsgCtx.getMessageType() == Sandesha2Constants.MessageTypes.APPLICATION) {
			//a ack may arrive. (not a application response)
			if (rmMsgCtx.getMessageContext().getAxisOperation()
					.getMessageExchangePattern().equals(
							org.apache.wsdl.WSDLConstants.MEP_URI_IN_OUT)) {
				responseExpected = true;
			}
		}

		return true;
	}

	public synchronized void runSenderForTheSequence(ConfigurationContext context, String sequenceID) {
		
		if (sequenceID!=null && !workingSequences.contains(sequenceID))
			workingSequences.add(sequenceID);
		

		if (!isSenderStarted()) {
			runSender = true;     //so that isSenderStarted()=true.
			super.start();
			this.context = context;
		}
	}

	private void updateMessage(MessageContext msgCtx1) throws SandeshaException {
//		try {
//			RMMsgContext rmMsgCtx1 = MsgInitializer.initializeMessage(msgCtx1);
//			rmMsgCtx1.addSOAPEnvelope();
//
//		} catch (AxisFault e) {
//			String message = "Exception in updating contexts";
//			log.debug(message);
//			throw new SandeshaException(message);
//		}

	}

	private void checkForSyncResponses(MessageContext msgCtx) {

		try {
			
			boolean responsePresent = (msgCtx
					.getProperty(MessageContext.TRANSPORT_IN) != null);
			if (!responsePresent)
				return;
			
			//we never expect sync responses. so we can freely create a new operation context for the incoming RM Specific message.
	        // create the responseMessageContext

			MessageContext responseMessageContext = new MessageContext();
			responseMessageContext.setConfigurationContext(msgCtx.getConfigurationContext());
			responseMessageContext.setSessionContext(msgCtx.getSessionContext());
			responseMessageContext.setTransportIn(msgCtx.getTransportIn());
			responseMessageContext.setTransportOut(msgCtx.getTransportOut());

	        responseMessageContext.setProperty(MessageContext.TRANSPORT_IN,
	        		msgCtx.getProperty(MessageContext.TRANSPORT_IN));
	       // msgCtx.getAxisOperation().registerOperationContext(responseMessageContext, msgCtx.getOperationContext());
	        responseMessageContext.setServerSide(false);
	        responseMessageContext.setServiceContext(msgCtx.getServiceContext());
	        responseMessageContext.setServiceGroupContext(msgCtx.getServiceGroupContext());

	        //we never expect sync responses. so we can freely create a new operation context for the incoming RM Specific message.
	        AxisOperation inOnlyOperation = AxisOperationFactory.getAxisOperation(AxisOperationFactory.MEP_CONSTANT_IN_ONLY);
	        inOnlyOperation.setRemainingPhasesInFlow(msgCtx.getAxisOperation().getRemainingPhasesInFlow());
	        
	        OperationContext inOnlyOperationContext = new OperationContext (inOnlyOperation);
	        responseMessageContext.setAxisOperation(inOnlyOperation);
	        responseMessageContext.setOperationContext(inOnlyOperationContext);
	        
	        // If request is REST we assume the responseMessageContext is REST, so set the variable
	        responseMessageContext.setDoingREST(msgCtx.isDoingREST());

	        SOAPEnvelope resenvelope = TransportUtils.createSOAPMessage(responseMessageContext,
	        		msgCtx.getEnvelope().getNamespace().getName());

	        if (resenvelope != null) {
	            responseMessageContext.setEnvelope(resenvelope);
	            AxisEngine engine = new AxisEngine(msgCtx.getConfigurationContext());
	            engine.receive(responseMessageContext);
	        } else {
	        	String message = "Exception is Receiving message...";
	        	log.debug(message);
	            throw new AxisFault(message);
	        }
			
			
			
			
			
			/*boolean responsePresent = (msgCtx
					.getProperty(MessageContext.TRANSPORT_IN) != null);

			if (responsePresent) {
				//create the response
				MessageContext response = new MessageContext(msgCtx
						.getConfigurationContext(), msgCtx.getSessionContext(),
						msgCtx.getTransportIn(), msgCtx.getTransportOut());
				response.setProperty(MessageContext.TRANSPORT_IN, msgCtx
						.getProperty(MessageContext.TRANSPORT_IN));

				response.setServerSide(false);

				//If request is REST we assume the response is REST, so set the
				// variable
				response.setDoingREST(msgCtx.isDoingREST());
				response.setServiceGroupContextId(msgCtx
						.getServiceGroupContextId());
				response
						.setServiceGroupContext(msgCtx.getServiceGroupContext());
				response.setServiceContext(msgCtx.getServiceContext());
				response.setAxisService(msgCtx.getAxisService());
				response.setAxisServiceGroup(msgCtx.getAxisServiceGroup());

				
				//setting the in-flow.
				//ArrayList inPhaseHandlers =
				// response.getAxisOperation().getRemainingPhasesInFlow();
				//
				// if (inPhaseHandlers==null || inPhaseHandlers.isEmpty()) {
				// ArrayList phases =
				// msgCtx.getSystemContext().getAxisConfiguration().getInPhasesUptoAndIncludingPostDispatch();
				// response.getAxisOperation().setRemainingPhasesInFlow(phases); }
				//

				//Changed following from TransportUtils to SandeshaUtil since
				// op.
				// context is anavailable.
				SOAPEnvelope resenvelope = null;
//				resenvelope = SandeshaUtil.createSOAPMessage(response, msgCtx
//						.getEnvelope().getNamespace().getName());

				resenvelope = TransportUtils.createSOAPMessage(response,msgCtx
  						.getEnvelope().getNamespace().getName());
				if (resenvelope != null) {
					AxisEngine engine = new AxisEngine(msgCtx
							.getConfigurationContext());
					response.setEnvelope(resenvelope);
					engine.receive(response);
				}
			} */

		} catch (Exception e) {
			log.info("No valid Sync response...");
		}
	}

}