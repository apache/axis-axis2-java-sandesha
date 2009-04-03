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

package org.apache.sandesha2.workers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.transport.RequestResponseTransport;
import org.apache.axis2.transport.TransportUtils;
import org.apache.axis2.transport.RequestResponseTransport.RequestResponseTransportStatus;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.Sandesha2Constants.MessageTypes;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;
import org.apache.sandesha2.policy.SandeshaPolicyBean;
import org.apache.sandesha2.storage.SandeshaStorageException;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.Transaction;
import org.apache.sandesha2.storage.beanmanagers.SenderBeanMgr;
import org.apache.sandesha2.storage.beans.RMDBean;
import org.apache.sandesha2.storage.beans.RMSBean;
import org.apache.sandesha2.storage.beans.SenderBean;
import org.apache.sandesha2.util.AcknowledgementManager;
import org.apache.sandesha2.util.MsgInitializer;
import org.apache.sandesha2.util.SandeshaUtil;
import org.apache.sandesha2.util.SequenceManager;
import org.apache.sandesha2.util.TerminateManager;

/**
 * This is responsible for sending and re-sending messages of Sandesha2. This
 * represent a thread that keep running all the time. This keep looking at the
 * Sender table to find out any entries that should be sent.
 */

public class Sender extends SandeshaThread {

	private static final Log log = LogFactory.getLog(Sender.class);

	// If this sender is working for several sequences, we use round-robin to
	// try and give them all a chance to invoke messages.
	int nextIndex = 0;

	boolean processedMessage = false;

	long lastHousekeeping = 0;
	
	long lastRanCleanup = 0;

	private static int HOUSEKEEPING_INTERVAL = 20000;

	private ConcurrentHashMap<String, AckHolder> ackMap = new ConcurrentHashMap<String, AckHolder>();
	
	private ConcurrentHashMap<String, Long> warnedAlreadyOrphans = new ConcurrentHashMap<String, Long>();

	private static class AckHolder {
		public long tts = 0;

		public RMMsgContext refMsg;
	}

	public Sender() {
		super(Sandesha2Constants.SENDER_SLEEP_TIME);
	}

	public void scheduleAddressableAcknowledgement(String sequenceId, long ackInterval, RMMsgContext ref) {
		AckHolder ackH = new AckHolder();
		ackH.tts = System.currentTimeMillis() + ackInterval;
		ackH.refMsg = ref;
		ackMap.putIfAbsent(sequenceId, ackH);
	}

	public void removeScheduledAcknowledgement(String sequenceId) {
		ackMap.remove(sequenceId);
	}

	protected boolean internalRun() {
		if (log.isDebugEnabled())
			log.debug("Enter: Sender::internalRun");

		Transaction transaction = null;
		boolean sleep = false;

		try {
			// Pick a sequence using a round-robin approach
			ArrayList<SequenceEntry> allSequencesList = getSequences();
			int size = allSequencesList.size();

			if (log.isDebugEnabled())
				log.debug("Choosing one from " + size + " sequences");
			if (nextIndex >= size) {
				nextIndex = 0;

				// We just looped over the set of sequences. If we didn't
				// process any
				// messages on this loop then we sleep before the next one
				if (size == 0 || !processedMessage) {
					sleep = true;
				}
				processedMessage = false;

				if (System.currentTimeMillis() - lastHousekeeping > HOUSEKEEPING_INTERVAL) {
					// At this point - delete any sequences that have timed out,
					// or been terminated.
					deleteTerminatedSequences(storageManager);

					// Also clean up and sender beans that are not yet eligible
					// for sending, but
					// are blocking the transport threads.
					unblockTransportThreads(storageManager);

					// Finally, check for messages that can only be serviced by
					// polling, and warn
					// the user if they are too old
					checkForOrphanMessages(storageManager);
					lastHousekeeping = System.currentTimeMillis();
				}
				if (log.isDebugEnabled())
					log.debug("Exit: Sender::internalRun, looped over all sequences, sleep " + sleep);
				return sleep;
			}

			transaction = storageManager.getTransaction();

			SequenceEntry entry = (SequenceEntry) allSequencesList.get(nextIndex++);
			String sequenceId = entry.getSequenceId();
			if (log.isDebugEnabled())
				log.debug("Chose sequence " + sequenceId);

			String rmVersion = null;
			// Check that the sequence is still valid
			boolean found = false;
			if (entry.isRmSource()) {
				RMSBean rms = null;
				if (entry.rmsKey == null) {
					RMSBean matcher = new RMSBean();
					matcher.setInternalSequenceID(sequenceId);
					matcher.setTerminated(false);
					rms = storageManager.getRMSBeanMgr().findUnique(matcher);
					if (rms != null) {
						entry.rmsKey = rms.getCreateSeqMsgID();
					}
				} else {
					rms = storageManager.getRMSBeanMgr().retrieve(entry.rmsKey);
					if(rms==null)
					{
						if (log.isDebugEnabled())
							log.debug("RMS bean is null - checking using findUnique");
					    RMSBean matcher = new RMSBean();
						matcher.setInternalSequenceID(sequenceId);
						matcher.setTerminated(false);
						rms = storageManager.getRMSBeanMgr().findUnique(matcher);
					}
				}
				if (rms != null && !rms.isTerminated() && !rms.isTimedOut()) {
					sequenceId = rms.getSequenceID();
					if (SequenceManager.hasSequenceTimedOut(rms, sequenceId, storageManager))
						SequenceManager.finalizeTimedOutSequence(rms.getInternalSequenceID(), null, storageManager);
					else
						found = true;
					rmVersion = rms.getRMVersion();
				}

			} else {
				RMDBean rmd = SandeshaUtil.getRMDBeanFromSequenceId(storageManager, sequenceId);
				if (rmd != null) {
					if (!rmd.isTerminated()) {
						found = true;
						rmVersion = rmd.getRMVersion();
					}

				}
			}
			if (!found) {
				stopThreadForSequence(sequenceId, entry.isRmSource());
				if (log.isDebugEnabled())
					log.debug("Exit: Sender::internalRun, sequence has ended");

				if (transaction != null && transaction.isActive()) {
					transaction.commit();
					transaction = null;
				}

				return false;
			}
			if (sequenceId != null) {
				AckHolder acktts = (AckHolder) ackMap.get(sequenceId);
				if (acktts != null && acktts.tts < System.currentTimeMillis()) {
					ackMap.remove(sequenceId);
					RMDBean rmd = storageManager.getRMDBeanMgr().retrieve(sequenceId);
					if (rmd != null) {
						RMMsgContext ackRMMsgContext = AcknowledgementManager.generateAckMessage(acktts.refMsg, rmd, sequenceId, storageManager, true);

						AcknowledgementManager.addAckBeanEntry(ackRMMsgContext, sequenceId, acktts.tts, storageManager);
						transaction.commit();
						transaction = storageManager.getTransaction();
					}

				}
			}
			SenderBeanMgr mgr = storageManager.getSenderBeanMgr();
			SenderBean senderBean = mgr.getNextMsgToSend(sequenceId);

			if (senderBean == null) {
				if (log.isDebugEnabled())
					log.debug("Exit: Sender::internalRun, no message for this sequence");

				if (transaction != null && transaction.isActive()) {
					transaction.commit();
					transaction = null;
				}

				return false; // Move on to the next sequence in the list
			}

			// work Id is used to define the piece of work that will be
			// assigned to the Worker thread,
			// to handle this Sender bean.

			// workId contains a timeTiSend part to cater for retransmissions.
			// This will cause retransmissions to be treated as new work.
			String workId = senderBean.getMessageID() + senderBean.getTimeToSend();

			// check weather the bean is already assigned to a worker.
			if (getWorkerLock().isWorkPresent(workId)) {
				// As there is already a worker running we are probably looping
				// too fast, so sleep on the next loop.
				if (log.isDebugEnabled()) {
					String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.workAlreadyAssigned, workId);
					log.debug("Exit: Sender::internalRun, " + message + ", sleeping");
				}

				if (transaction != null && transaction.isActive()) {
					transaction.commit();
					transaction = null;
				}

				return true;
			}

			// commiting the transaction here to release resources early.
			if (transaction != null && transaction.isActive())
				transaction.commit();
			transaction = null;

			// start a worker which will work on this messages.
			SenderWorker worker = new SenderWorker(context, senderBean, rmVersion);
			worker.setLock(getWorkerLock());
			worker.setWorkId(workId);

			try {
				// Set the lock up before we start the thread, but roll it back
				// if we hit any problems
				getWorkerLock().addWork(workId, worker);
				threadPool.execute(worker);
			} catch (Exception e) {
				getWorkerLock().removeWork(workId);
			}

			// If we got to here then we found work to do on the sequence, so we
			// should
			// remember not to sleep at the end of the list of sequences.
			processedMessage = true;

		} catch (Exception e) {

			// TODO : when this is the client side throw the exception to
			// the client when necessary.

			// TODO rollback only if a SandeshaStorageException.
			// This allows the other Exceptions to be used within the Normal
			// flow.

			String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.sendMsgError, e.toString());
			log.debug(message, e);
		} finally {
			if (transaction != null && transaction.isActive()) {
				try {
					transaction.rollback();
					transaction = null;
				} catch (Exception e) {
					String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.rollbackError, e.toString());
					log.debug(message, e);
				}
			}
		}
		if (log.isDebugEnabled())
			log.debug("Exit: Sender::internalRun, not sleeping");
		return false;
	}

	/**
	 * Finds any RMDBeans that have not been used inside the set
	 * InnactivityTimeoutInterval
	 * 
	 * Iterates through RMSBeans and RMDBeans that have been terminated or timed
	 * out and deletes them.
	 * 
	 */
	private void deleteTerminatedSequences(StorageManager storageManager) {
		if (log.isDebugEnabled())
			log.debug("Enter: Sender::deleteTerminatedSequences");

		RMSBean finderBean = new RMSBean();
		finderBean.setTerminated(true);

		Transaction transaction = null;

		try {
			transaction = storageManager.getTransaction();

			SandeshaPolicyBean propertyBean = SandeshaUtil.getPropertyBean(storageManager.getContext().getAxisConfiguration());

			long deleteTime = propertyBean.getSequenceRemovalTimeoutInterval();
			if (deleteTime < 0)
				deleteTime = 0;

			if (deleteTime > 0) {
				// Find terminated sequences.
				List<RMSBean> rmsBeans = storageManager.getRMSBeanMgr().find(finderBean);

				deleteRMSBeans(rmsBeans, propertyBean, deleteTime);

				finderBean.setTerminated(false);
				finderBean.setTimedOut(true);

				// Find timed out sequences
				rmsBeans = storageManager.getRMSBeanMgr().find(finderBean);

				deleteRMSBeans(rmsBeans, propertyBean, deleteTime);

				// Remove any terminated RMDBeans.
				RMDBean finderRMDBean = new RMDBean();
				finderRMDBean.setTerminated(true);

				List<RMDBean> rmdBeans = storageManager.getRMDBeanMgr().find(finderRMDBean);

				Iterator<RMDBean> beans = rmdBeans.iterator();
				while (beans.hasNext()) {
					RMDBean rmdBean = (RMDBean) beans.next();

					long timeNow = System.currentTimeMillis();
					long lastActivated = rmdBean.getLastActivatedTime();

					// delete sequences that have been timedout or deleted for
					// more than
					// the SequenceRemovalTimeoutInterval
					if ((lastActivated + deleteTime) < timeNow) {
						if (log.isDebugEnabled())
							log.debug("Deleting RMDBean " + deleteTime + " : " + rmdBean);
						storageManager.getRMDBeanMgr().delete(rmdBean.getSequenceID());
					}
				}
			}

			// Terminate RMD Sequences that have been inactive.
			if (propertyBean.getInactivityTimeoutInterval() > 0) {
				RMDBean finderRMDBean = new RMDBean();
				finderRMDBean.setTerminated(false);

				List<RMDBean> rmdBeans = storageManager.getRMDBeanMgr().find(finderRMDBean);

				Iterator<RMDBean> beans = rmdBeans.iterator();
				while (beans.hasNext()) {
					RMDBean rmdBean = (RMDBean) beans.next();

					long timeNow = System.currentTimeMillis();
					long lastActivated = rmdBean.getLastActivatedTime();

					if ((lastActivated + propertyBean.getInactivityTimeoutInterval()) < timeNow) {
						// Terminate
						rmdBean.setTerminated(true);
						rmdBean.setLastActivatedTime(timeNow);
						if (log.isDebugEnabled())
							log.debug(System.currentTimeMillis() + "Marking RMDBean as terminated " + rmdBean);
						storageManager.getRMDBeanMgr().update(rmdBean);
					}
				}
			}

			if (transaction != null && transaction.isActive())
				transaction.commit();

		} catch (SandeshaException e) {
			if (log.isErrorEnabled())
				log.error(e);
		} finally {
			if (transaction != null && transaction.isActive()) {
				try {
					transaction.rollback();
				} catch (SandeshaStorageException e) {
					if (log.isDebugEnabled())
						log.debug("Caught exception rolling back transaction", e);
				}
			}
		}

		if (log.isDebugEnabled())
			log.debug("Exit: Sender::deleteTerminatedSequences");
	}

	private void deleteRMSBeans(List<RMSBean> rmsBeans, SandeshaPolicyBean propertyBean, long deleteTime)

	throws SandeshaStorageException, SandeshaException {
		if (log.isDebugEnabled())
			log.debug("Enter: Sender::deleteRMSBeans");

		Iterator<RMSBean> beans = rmsBeans.iterator();

		while (beans.hasNext()) {
			RMSBean rmsBean = (RMSBean) beans.next();
			long timeNow = System.currentTimeMillis();
			long lastActivated = rmsBean.getLastActivatedTime();

			// delete sequences that have been timedout or deleted for more than
			// the SequenceRemovalTimeoutInterval
			if (((lastActivated + deleteTime) < timeNow) &&
				(rmsBean.isReallocated() == Sandesha2Constants.WSRM_COMMON.NOT_REALLOCATED)) {
				if (log.isDebugEnabled())
					log.debug("Removing RMSBean " + rmsBean);

				//Need to check if it's an RMSBean created for reallocation.  If so we need to
				//delete the original RMSBean that was reallocated.
				RMSBean reallocatedRMSBean = SandeshaUtil.isLinkedToReallocatedRMSBean(storageManager, rmsBean.getInternalSequenceID());
				
				if(reallocatedRMSBean != null){
					if (log.isDebugEnabled())
						log.debug("Removing Reallocated RMSBean " + reallocatedRMSBean);
					storageManager.getRMSBeanMgr().delete(reallocatedRMSBean.getCreateSeqMsgID());
				}

				storageManager.getRMSBeanMgr().delete(rmsBean.getCreateSeqMsgID());
				storageManager.removeMessageContext(rmsBean.getReferenceMessageStoreKey());
			}
		}

		if (log.isDebugEnabled())
			log.debug("Exit: Sender::deleteRMSBeans");
	}

	private void unblockTransportThreads(StorageManager manager) throws SandeshaStorageException {
		if (log.isDebugEnabled())
			log.debug("Enter: Sender::unblockTransportThreads");

		Transaction transaction = null;
		try {
			transaction = manager.getTransaction();

			// This finder will look for beans that have been locking the
			// transport for longer than
			// the TRANSPORT_WAIT_TIME. The match method for SenderBeans does
			// the time comparison
			// for us.
			SenderBean finder = new SenderBean();
			finder.setSend(false);
			finder.setTransportAvailable(true);
			finder.setTimeToSend(System.currentTimeMillis() - Sandesha2Constants.TRANSPORT_WAIT_TIME);

			List<SenderBean> beans = manager.getSenderBeanMgr().find(finder);
			Iterator<SenderBean> beanIter = beans.iterator();
			while (beanIter.hasNext()) {
				// The beans we have found are assigned to an internal sequence
				// id, but the create
				// sequence has not completed yet (and perhaps never will).
				// Server-side, most of the
				// info that we can usefully print is associated with the
				// inbound sequence that generated
				// this message.
				SenderBean bean = (SenderBean) beanIter.next();

				// Load the message, so that we can free the transport (if there
				// is one there). The
				// case we are trying to free up is when there is a
				// request-response transport, and
				// it's still there waiting.
				MessageContext msgCtx = manager.retrieveMessageContext(bean.getMessageContextRefKey(), context);

				RequestResponseTransport t = null;
				MessageContext inMsg = null;
				OperationContext op = msgCtx.getOperationContext();
				if (op != null)
					inMsg = op.getMessageContext(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
				if (inMsg != null)
					t = (RequestResponseTransport) inMsg.getProperty(RequestResponseTransport.TRANSPORT_CONTROL);

				if ((t != null && RequestResponseTransportStatus.WAITING.equals(t.getStatus()))) {
					if (log.isWarnEnabled()) {
						String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.freeingTransport);
						log.warn(message);
					}
					// If the message is a reply, then the request may need to
					// be acked. Rather
					// than just return a HTTP 202, we should try to send an
					// ack.
					boolean sendAck = false;
					RMDBean inbound = null;
					String inboundSeq = bean.getInboundSequenceId();
					if (inboundSeq != null)
						inbound = SandeshaUtil.getRMDBeanFromSequenceId(manager, inboundSeq);

					if (inbound != null) {
						EndpointReference acksToEPR = inbound.getAcksToEndpointReference();
						if (acksToEPR != null && acksToEPR.hasAnonymousAddress())
							sendAck = true;
					}

					if (sendAck) {
						RMMsgContext rmMsgCtx = MsgInitializer.initializeMessage(msgCtx);
						RMMsgContext ackRMMsgCtx = AcknowledgementManager.generateAckMessage(rmMsgCtx, inbound, inbound.getSequenceID(), storageManager, true);
						AcknowledgementManager.sendAckNow(ackRMMsgCtx);
						TransportUtils.setResponseWritten(msgCtx, true);
					} else {
						TransportUtils.setResponseWritten(msgCtx, false);
					}

					// Mark the bean so that we know the transport is missing,
					// and reset the send time
					bean.setTransportAvailable(false);
					bean.setTimeToSend(System.currentTimeMillis());

					// Update the bean
					manager.getSenderBeanMgr().update(bean);
				}
			}

			if (transaction != null && transaction.isActive())
				transaction.commit();
			transaction = null;

		} catch (Exception e) {
			// There isn't much we can do here, so log the exception and
			// continue.
			if (log.isDebugEnabled())
				log.debug("Exception", e);
		} finally {
			if (transaction != null && transaction.isActive())
				transaction.rollback();
		}

		if (log.isDebugEnabled())
			log.debug("Exit: Sender::unblockTransportThreads");
	}

	private void checkForOrphanMessages(StorageManager manager) throws SandeshaStorageException {
		if (log.isDebugEnabled())
			log.debug("Enter: Sender::checkForOrphanMessages");

		Transaction tran = null;
		try {
			tran = manager.getTransaction();

			// This finder will look for beans that should have been sent, but
			// could not be sent
			// because they need a MakeConnection message to come in to pick it
			// up. We also factor
			// in TRANSPORT_WAIT_TIME to give the MakeConnection a chance to
			// arrive.
			SenderBean finder = new SenderBean();
			finder.setSend(true);
			finder.setTransportAvailable(false);
			finder.setTimeToSend(System.currentTimeMillis() - Sandesha2Constants.TRANSPORT_WAIT_TIME);

			List<SenderBean> beans = manager.getSenderBeanMgr().find(finder);
			
			//Commit this transaction
			tran.commit();
			
			// Create a new transaction
			tran = manager.getTransaction();

			Iterator<SenderBean> beanIter = beans.iterator();
			while (beanIter.hasNext()) {
				SenderBean bean = (SenderBean) beanIter.next();

				// Emit a message to warn the user that MakeConnections are not
				// arriving to pick
				// messages up
				if (log.isWarnEnabled()) {
					String message = null;
					String internalSequenceID = bean.getInternalSequenceID();
					String sequenceID = bean.getSequenceID();
				
					if(!warnedAlreadyOrphans.containsKey(sequenceID)){ // we only want to do log.warn once per orphaned sequenceId
					
						if (bean.getMessageType() == Sandesha2Constants.MessageTypes.APPLICATION)					
							message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.noPolling, sequenceID, internalSequenceID);				
						else
						{
							String messageType = Integer.toString(bean.getMessageType());
							message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.noPollingProtocol, messageType, sequenceID, internalSequenceID);
						}
						warnedAlreadyOrphans.put(sequenceID, System.currentTimeMillis());
						log.warn(message);
 					}
 				}
				
				// If client shuts down too quickly, termination messages get orphaned and this has an impact on performance.  
				// Will delete these once they have been recognised as orphans.
				int messageType = bean.getMessageType();
				if(MessageTypes.TERMINATE_SEQ ==  messageType || MessageTypes.TERMINATE_SEQ_RESPONSE ==  messageType){
					String id = bean.getSequenceID(); // get this again as it is an error case
					
					// Mark the sequence as terminated
					RMSBean rmsBean = SandeshaUtil.getRMSBeanFromSequenceId(manager, id);
					TerminateManager.terminateSendingSide(rmsBean, storageManager, false, null);
					
					if(log.isDebugEnabled()) log.debug("Sender::checkForOrphanMessages.  Orphaned message of type TERMINATE_SEQ or TERMINATE_SEQ_RESPONSE found.  Deleting this message with a sequence ID of : " + id);
					// Delete the terminate sender bean.
					manager.getSenderBeanMgr().delete(bean.getMessageID());		
					
				} else {					
					// Update the bean so that we won't emit another message for another TRANSPORT_WAIT_TIME
					bean.setTimeToSend(System.currentTimeMillis());
					manager.getSenderBeanMgr().update(bean);
				}
			
				// clean up warnedAlreadyOrphans list when it gets big - currently over a thousand entries, or every10 minutes
				// delete everything over an hour old
				long currentTime = System.currentTimeMillis();
				if(lastRanCleanup == 0){
					lastRanCleanup = System.currentTimeMillis();
				}
				if( warnedAlreadyOrphans.size() > 1000 || currentTime > (lastRanCleanup + 600000)){
					if(log.isDebugEnabled()) log.debug("Sender::checkForOrphanMessages.  Cleaning up list of orphans");
					long timeAnHourAgo = currentTime - 3600000; 
					Iterator<String> it = warnedAlreadyOrphans.keySet().iterator();
					while(it.hasNext()){
						Object key = it.next();
						long ageOfThisOrphan = ((Long)warnedAlreadyOrphans.get(key)).longValue();
						if (ageOfThisOrphan < timeAnHourAgo) {
							warnedAlreadyOrphans.remove(key);
						}
						
					}
					lastRanCleanup = System.currentTimeMillis();
				}

			}

			if (tran != null && tran.isActive())
				tran.commit();
			tran = null;

		} catch (Exception e) {
			// There isn't much we can do here, so log the exception and
			// continue.
			if (log.isDebugEnabled())
				log.debug("Exception", e);
		} finally {
			if (tran != null && tran.isActive())
				tran.rollback();
		}

		if (log.isDebugEnabled())
			log.debug("Exit: Sender::checkForOrphanMessages");
	}


}
