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

package org.apache.sandesha2.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.beanmanagers.RMSBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.InvokerBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.RMDBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.SenderBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.SequencePropertyBeanMgr;
import org.apache.sandesha2.storage.beans.RMSBean;
import org.apache.sandesha2.storage.beans.InvokerBean;
import org.apache.sandesha2.storage.beans.RMDBean;
import org.apache.sandesha2.storage.beans.SenderBean;
import org.apache.sandesha2.storage.beans.SequencePropertyBean;

/**
 * Contains logic to remove all the storad data of a sequence. Methods of this
 * are called by sending side and the receiving side when appropriate
 */

public class TerminateManager {

	private static Log log = LogFactory.getLog(TerminateManager.class);

	private static String CLEANED_ON_TERMINATE_MSG = "CleanedOnTerminateMsg";

	private static String CLEANED_AFTER_INVOCATION = "CleanedAfterInvocation";

	public static HashMap receivingSideCleanMap = new HashMap();

	/**
	 * Called by the receiving side to remove data related to a sequence. e.g.
	 * After sending the TerminateSequence message. Calling this methods will
	 * complete all the data if InOrder invocation is not sequired.
	 * 
	 * @param configContext
	 * @param sequenceID
	 * @throws SandeshaException
	 */
	public static void cleanReceivingSideOnTerminateMessage(ConfigurationContext configContext, String sequencePropertyKey ,String sequenceId,
			StorageManager storageManager) throws SandeshaException {
		// clean senderMap

		// Currently in-order invocation is done for default values.
		boolean inOrderInvocation = SandeshaUtil.getDefaultPropertyBean(configContext.getAxisConfiguration())
				.isInOrder();

		if (!inOrderInvocation) {
			// there is no invoking by Sandesha2. So clean invocations storages.
			
			receivingSideCleanMap.put(sequenceId, CLEANED_ON_TERMINATE_MSG);
			cleanReceivingSideAfterInvocation(configContext, sequencePropertyKey, sequenceId, storageManager);
		} else {

			String cleanStatus = (String) receivingSideCleanMap.get(sequenceId);
			if (cleanStatus != null
					&& CLEANED_AFTER_INVOCATION.equals(cleanStatus))
				completeTerminationOfReceivingSide(configContext,
						sequencePropertyKey, sequenceId, storageManager);
			else
				receivingSideCleanMap.put(sequenceId, CLEANED_ON_TERMINATE_MSG);
		}
	}

	/**
	 * When InOrder invocation is anabled this had to be called to clean the
	 * data left by the above method. This had to be called after the Invocation
	 * of the Last Message.
	 * 
	 * @param configContext
	 * @param sequenceID
	 * @throws SandeshaException
	 */
	public static void cleanReceivingSideAfterInvocation(ConfigurationContext configContext, String sequencePropertyKey ,String sequenceId,
			StorageManager storageManager) throws SandeshaException {
		InvokerBeanMgr storageMapBeanMgr = storageManager.getStorageMapBeanMgr();

		// removing storageMap entries
		InvokerBean findStorageMapBean = new InvokerBean();
		findStorageMapBean.setSequenceID(sequenceId);
		findStorageMapBean.setInvoked(true);
		Collection collection = storageMapBeanMgr.find(findStorageMapBean);
		Iterator iterator = collection.iterator();
		while (iterator.hasNext()) {
			InvokerBean storageMapBean = (InvokerBean) iterator.next();
			storageMapBeanMgr.delete(storageMapBean.getMessageContextRefKey());

			// removing the respective message context from the message store.
			// If this is an in-only message.
			// In-out message will be deleted when a ack is retrieved for the
			// out message.
			String messageStoreKey = storageMapBean.getMessageContextRefKey();
			storageManager.removeMessageContext(messageStoreKey);

		}

		String cleanStatus = (String) receivingSideCleanMap.get(sequenceId);
		if (cleanStatus != null && CLEANED_ON_TERMINATE_MSG.equals(cleanStatus))
			completeTerminationOfReceivingSide(configContext, sequencePropertyKey, sequenceId, storageManager);
		else {
			receivingSideCleanMap.put(sequenceId, CLEANED_AFTER_INVOCATION);
		}
	}

	/**
	 * This has to be called by the lastly invocated one of the above two
	 * methods.
	 * 
	 */
	private static void completeTerminationOfReceivingSide(ConfigurationContext configContext, String sequencePropertyKey,String sequenceId,
			StorageManager storageManager) throws SandeshaException {

		removeReceivingSideProperties(configContext, sequencePropertyKey, sequenceId, storageManager);
		
		// removing nextMsgMgr entries
		RMDBeanMgr rMDBeanMgr = storageManager.getNextMsgBeanMgr();
		RMDBean findNextMsgBean = new RMDBean();
		findNextMsgBean.setSequenceID(sequenceId);
		Collection collection = rMDBeanMgr.find(findNextMsgBean);
		Iterator iterator = collection.iterator();
		while (iterator.hasNext()) {
			RMDBean rMDBean = (RMDBean) iterator.next();
			 rMDBeanMgr.delete(rMDBean.getSequenceID());
		}

	}

	private static void removeReceivingSideProperties(ConfigurationContext configContext, String sequencePropertyKey, 
			String sequenceId, StorageManager storageManager) throws SandeshaException {
		SequencePropertyBeanMgr sequencePropertyBeanMgr = storageManager.getSequencePropertyBeanMgr();
		SequencePropertyBean allSequenceBean = sequencePropertyBeanMgr.retrieve(
				Sandesha2Constants.SequenceProperties.ALL_SEQUENCES,
				Sandesha2Constants.SequenceProperties.INCOMING_SEQUENCE_LIST);

		if (allSequenceBean != null) {
			log.debug("AllSequence bean is null");

			ArrayList allSequenceList = SandeshaUtil.getArrayListFromString(allSequenceBean.getValue());
			allSequenceList.remove(sequenceId);

			// updating
			allSequenceBean.setValue(allSequenceList.toString());
			sequencePropertyBeanMgr.update(allSequenceBean);
		}
	}

	/**
	 * This is called by the sending side to clean data related to a sequence.
	 * e.g. after sending the TerminateSequence message.
	 * 
	 * @param configContext
	 * @param sequenceID
	 * @throws SandeshaException
	 */
	public static void terminateSendingSide(ConfigurationContext configContext, String sequencePropertyKey, String internalSequenceID,
			boolean serverSide, StorageManager storageManager) throws SandeshaException {

		SequencePropertyBeanMgr seqPropMgr = storageManager.getSequencePropertyBeanMgr();

		SequencePropertyBean seqTerminatedBean = new SequencePropertyBean(internalSequenceID,
				Sandesha2Constants.SequenceProperties.SEQUENCE_TERMINATED, Sandesha2Constants.VALUE_TRUE);
		seqPropMgr.insert(seqTerminatedBean);

		cleanSendingSideData (configContext, sequencePropertyKey , internalSequenceID, serverSide, storageManager);
	}

	private static void doUpdatesIfNeeded(String sequenceID, SequencePropertyBean propertyBean,
			SequencePropertyBeanMgr seqPropMgr) throws SandeshaException {

		boolean addEntryWithSequenceID = false;

		if (propertyBean.getName().equals(Sandesha2Constants.SequenceProperties.CLIENT_COMPLETED_MESSAGES)) {
			addEntryWithSequenceID = true;
		}

		if (propertyBean.getName().equals(Sandesha2Constants.SequenceProperties.SEQUENCE_TERMINATED)) {
			addEntryWithSequenceID = true;
		}

		if (propertyBean.getName().equals(Sandesha2Constants.SequenceProperties.SEQUENCE_CLOSED)) {
			addEntryWithSequenceID = true;
		}

		if (propertyBean.getName().equals(Sandesha2Constants.SequenceProperties.SEQUENCE_TIMED_OUT)) {
			addEntryWithSequenceID = true;
		}

		if (addEntryWithSequenceID && sequenceID != null) {
			// this value cannot be completely deleted since this data will be
			// needed by SequenceReports
			// so saving it with the sequenceID value being the out sequenceID.

			SequencePropertyBean newBean = new SequencePropertyBean();
			newBean.setSequencePropertyKey(sequenceID);
			newBean.setName(propertyBean.getName());
			newBean.setValue(propertyBean.getValue());

			seqPropMgr.insert(newBean);
			// TODO amazingly this property does not seem to get deleted without
			// following - in the hibernate impl
			// (even though the lines efter current methodcall do this).
			seqPropMgr.delete(propertyBean.getSequencePropertyKey(), propertyBean.getName());
		}
	}

	private static boolean isPropertyDeletable(String name) {
		boolean deleatable = true;

		if (Sandesha2Constants.SequenceProperties.TERMINATE_ADDED.equals(name))
			deleatable = false;

		if (Sandesha2Constants.SequenceProperties.NO_OF_OUTGOING_MSGS_ACKED.equals(name))
			deleatable = false;

		if (Sandesha2Constants.SequenceProperties.INTERNAL_SEQUENCE_ID.equals(name))
			deleatable = false;

		// if
		// (Sandesha2Constants.SequenceProperties.RM_SPEC_VERSION.equals(name))
		// deleatable = false;

		if (Sandesha2Constants.SequenceProperties.SEQUENCE_TERMINATED.equals(name))
			deleatable = false;

		if (Sandesha2Constants.SequenceProperties.SEQUENCE_CLOSED.equals(name))
			deleatable = false;

		if (Sandesha2Constants.SequenceProperties.SEQUENCE_TIMED_OUT.equals(name))
			deleatable = false;

		return deleatable;
	}

	public static void timeOutSendingSideSequence(ConfigurationContext context, String sequencePropertyKey,String internalSequenceId,
			boolean serverside, StorageManager storageManager) throws SandeshaException {

		SequencePropertyBeanMgr seqPropMgr = storageManager.getSequencePropertyBeanMgr();
		SequencePropertyBean seqTerminatedBean = new SequencePropertyBean(sequencePropertyKey,
				Sandesha2Constants.SequenceProperties.SEQUENCE_TIMED_OUT, Sandesha2Constants.VALUE_TRUE);
		seqPropMgr.insert(seqTerminatedBean);

		cleanSendingSideData(context, sequencePropertyKey,internalSequenceId, serverside, storageManager);
	}

	private static void cleanSendingSideData(ConfigurationContext configContext, String sequencePropertyKey,String internalSequenceId,
			boolean serverSide, StorageManager storageManager) throws SandeshaException {

		SequencePropertyBeanMgr sequencePropertyBeanMgr = storageManager.getSequencePropertyBeanMgr();
		SenderBeanMgr retransmitterBeanMgr = storageManager.getRetransmitterBeanMgr();
		RMSBeanMgr rMSBeanMgr = storageManager.getCreateSeqBeanMgr();

		// removing retransmitterMgr entries and corresponding message contexts.
		Collection collection = retransmitterBeanMgr.find(internalSequenceId);
		Iterator iterator = collection.iterator();
		while (iterator.hasNext()) {
			SenderBean retransmitterBean = (SenderBean) iterator.next();
			retransmitterBeanMgr.delete(retransmitterBean.getMessageID());

			String messageStoreKey = retransmitterBean.getMessageContextRefKey();
			storageManager.removeMessageContext(messageStoreKey);
		}

		// removing the createSeqMgrEntry
		RMSBean createSeqFindBean = new RMSBean();
		createSeqFindBean.setInternalSequenceID(internalSequenceId);

		RMSBean rMSBean = storageManager.getCreateSeqBeanMgr().findUnique(createSeqFindBean);
		rMSBeanMgr.delete(rMSBean.getCreateSeqMsgID());

		String outSequenceID = rMSBean.getSequenceID();
		
		// removing sequence properties
		SequencePropertyBean findSequencePropertyBean1 = new SequencePropertyBean();
		findSequencePropertyBean1.setSequencePropertyKey(sequencePropertyKey);
		collection = sequencePropertyBeanMgr.find(findSequencePropertyBean1);
		iterator = collection.iterator();
		while (iterator.hasNext()) {
			SequencePropertyBean sequencePropertyBean = (SequencePropertyBean) iterator.next();
			doUpdatesIfNeeded(outSequenceID, sequencePropertyBean, sequencePropertyBeanMgr);

			// TODO all properties which hv the temm:Seq:id as the key should be
			// deletable.
			if (isPropertyDeletable(sequencePropertyBean.getName())) {
				sequencePropertyBeanMgr.delete(sequencePropertyBean.getSequencePropertyKey(), sequencePropertyBean.getName());
			}
		}
	}

	public static void addTerminateSequenceMessage(RMMsgContext referenceMessage, String internalSequenceID, String outSequenceId,
			String sequencePropertyKey, StorageManager storageManager) throws AxisFault {
		if(log.isDebugEnabled())
			log.debug("Enter: TerminateManager::addTerminateSequenceMessage " + outSequenceId + ", " + internalSequenceID);

		SequencePropertyBeanMgr seqPropMgr = storageManager.getSequencePropertyBeanMgr();

		SequencePropertyBean terminated = seqPropMgr.retrieve(internalSequenceID,
				Sandesha2Constants.SequenceProperties.TERMINATE_ADDED);

		if (terminated != null && terminated.getValue() != null && "true".equals(terminated.getValue())) {
			if(log.isDebugEnabled())
				log.debug("Exit: TerminateManager::addTerminateSequenceMessage - terminate was added previously.");
			return;
		}

		RMMsgContext terminateRMMessage = RMMsgCreator.createTerminateSequenceMessage(referenceMessage, outSequenceId,
				sequencePropertyKey, storageManager);
		terminateRMMessage.setFlow(MessageContext.OUT_FLOW);
		terminateRMMessage.setProperty(Sandesha2Constants.APPLICATION_PROCESSING_DONE, "true");

		
		//setting the To EPR.
		//First try to get it from an Endpoint property.
		//If not get it from the To property.
		
		EndpointReference toEPR = null;
		
		SequencePropertyBean endpointBean = seqPropMgr.retrieve(sequencePropertyKey,
				Sandesha2Constants.SequenceProperties.OFFERED_ENDPOINT);
		if (endpointBean!=null) {
			toEPR = new EndpointReference (endpointBean.getValue());
		}
		
		if (toEPR==null) {
			
			SequencePropertyBean toBean = seqPropMgr.retrieve(sequencePropertyKey,
				Sandesha2Constants.SequenceProperties.TO_EPR);

			if (toBean!=null) {
				toEPR = new EndpointReference(toBean.getValue());
				if (toEPR == null) {
					String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.toEPRNotValid, null);
					throw new SandeshaException(message);
				}
			}
		}

		if (toEPR!=null)
			terminateRMMessage.setTo(toEPR);
		
		SequencePropertyBean replyToBean = seqPropMgr.retrieve(sequencePropertyKey,
				Sandesha2Constants.SequenceProperties.REPLY_TO_EPR);
		if (replyToBean!=null) {
			terminateRMMessage.setReplyTo(new EndpointReference (replyToBean.getValue()));
		}
		
		String rmVersion = SandeshaUtil.getRMVersion(sequencePropertyKey, storageManager);
		if (rmVersion == null)
			throw new SandeshaException(SandeshaMessageHelper.getMessage(SandeshaMessageKeys.cannotDecideRMVersion));
		terminateRMMessage.setWSAAction(SpecSpecificConstants.getTerminateSequenceAction(rmVersion));
		terminateRMMessage.setSOAPAction(SpecSpecificConstants.getTerminateSequenceSOAPAction(rmVersion));

		SequencePropertyBean transportToBean = seqPropMgr.retrieve(sequencePropertyKey,
				Sandesha2Constants.SequenceProperties.TRANSPORT_TO);
		if (transportToBean != null) {
			terminateRMMessage.setProperty(Constants.Configuration.TRANSPORT_URL, transportToBean.getValue());
		}

		terminateRMMessage.addSOAPEnvelope();

		String key = SandeshaUtil.getUUID();

		SenderBean terminateBean = new SenderBean();
		terminateBean.setMessageContextRefKey(key);

		// Set a retransmitter lastSentTime so that terminate will be send with
		// some delay.
		// Otherwise this get send before return of the current request (ack).
		// TODO: refine the terminate delay.
		terminateBean.setTimeToSend(System.currentTimeMillis() + Sandesha2Constants.TERMINATE_DELAY);

		terminateBean.setMessageID(terminateRMMessage.getMessageId());

		// this will be set to true at the sender.
		terminateBean.setSend(true);

		terminateRMMessage.getMessageContext().setProperty(Sandesha2Constants.QUALIFIED_FOR_SENDING,
				Sandesha2Constants.VALUE_FALSE);

		terminateBean.setReSend(false);
		
		EndpointReference to = terminateRMMessage.getTo();
		if (to!=null)
			terminateBean.setToAddress(to.getAddress());

		SequencePropertyBean terminateAdded = new SequencePropertyBean();
		terminateAdded.setName(Sandesha2Constants.SequenceProperties.TERMINATE_ADDED);
		terminateAdded.setSequencePropertyKey(internalSequenceID);
		terminateAdded.setValue("true");

		seqPropMgr.insert(terminateAdded);

		terminateRMMessage.setProperty(Sandesha2Constants.SET_SEND_TO_TRUE, Sandesha2Constants.VALUE_TRUE);
		
		//the propertyKey of the ackMessage will be the propertyKey for the terminate message as well.
		terminateRMMessage.setProperty(Sandesha2Constants.MessageContextProperties.SEQUENCE_PROPERTY_KEY, sequencePropertyKey);
		
		// / addTerminateSeqTransaction.commit();
		SandeshaUtil.executeAndStore(terminateRMMessage, key);
		
		SenderBeanMgr retramsmitterMgr = storageManager.getRetransmitterBeanMgr();
		retramsmitterMgr.insert(terminateBean);

		if(log.isDebugEnabled())
			log.debug("Exit: TerminateManager::addTerminateSequenceMessage");
	}

}
