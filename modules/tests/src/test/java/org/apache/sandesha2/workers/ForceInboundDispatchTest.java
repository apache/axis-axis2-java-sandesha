/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sandesha2.workers;

import java.io.File;

import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.SandeshaTestCase;
import org.apache.sandesha2.client.SandeshaClient;
import org.apache.sandesha2.client.SandeshaClientConstants;
import org.apache.sandesha2.client.SequenceReport;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.Transaction;
import org.apache.sandesha2.storage.beans.RMDBean;
import org.apache.sandesha2.storage.beans.RMSBean;
import org.apache.sandesha2.util.RangeString;
import org.apache.sandesha2.util.SandeshaUtil;

public class ForceInboundDispatchTest extends SandeshaTestCase  {

	private static ConfigurationContext serverConfigCtx = null;
	private boolean startedServer = false;
	
	public ForceInboundDispatchTest () {
        super ("ForceDispatchTest");
	}
	
	public void setUp () throws Exception {
		super.setUp();
		String repoPath = "target" + File.separator + "repos" + File.separator + "server";
		String axis2_xml = "target" + File.separator + "repos" + File.separator + "server" + File.separator + "server_axis2.xml";
		if (!startedServer)
			serverConfigCtx = startServer(repoPath, axis2_xml);
		startedServer = true;
	}
	
	/**
	 * Override the teardown processing
	 */
	public void tearDown () throws Exception {
		super.tearDown();
	}

	public void testForceInvoke () throws AxisFault,InterruptedException  {
		
		String to = "http://127.0.0.1:" + serverPort + "/axis2/services/RMSampleService";
		
		String repoPath = "target" + File.separator + "repos" + File.separator + "client";
		String axis2_xml = "target" + File.separator + "repos" + File.separator + "client" + File.separator + "client_axis2.xml";

		ConfigurationContext configContext = ConfigurationContextFactory.createConfigurationContextFromFileSystem(repoPath,axis2_xml);

		Options clientOptions = new Options ();
		clientOptions.setAction(pingAction);
		clientOptions.setSoapVersionURI(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
		
		clientOptions.setTo(new EndpointReference (to));
		
		String sequenceKey = "sequence1";
		clientOptions.setProperty(SandeshaClientConstants.SEQUENCE_KEY,sequenceKey);
		ServiceClient serviceClient = new ServiceClient (configContext,null);
		serviceClient.setOptions(clientOptions);
		
		try{
			serviceClient.fireAndForget(getPingOMBlock("ping1"));		
			
			//now deliver the next out of order 
			clientOptions.setProperty(SandeshaClientConstants.MESSAGE_NUMBER,new Long(3));
			serviceClient.fireAndForget(getPingOMBlock("ping3"));
	
			Thread.sleep(5000);
			StorageManager mgr = SandeshaUtil.getInMemoryStorageManager(configContext);
			Transaction t = mgr.getTransaction();
			String inboundSequenceID = SandeshaUtil.getSequenceIDFromInternalSequenceID(SandeshaUtil.getInternalSequenceID(to, sequenceKey),
					mgr);
			t.commit();
			
			SandeshaClient.forceDispatchOfInboundMessages(serverConfigCtx, 
					inboundSequenceID, 
					true); //allow later msgs to be delivered 
			
			//check that the server is now expecting msg 4
			StorageManager serverStore = SandeshaUtil.getInMemoryStorageManager(serverConfigCtx);
			t = serverStore.getTransaction();
			RMDBean rMDBean = 
				serverStore.getRMDBeanMgr().retrieve(inboundSequenceID);
			assertNotNull(rMDBean);
			assertEquals(rMDBean.getNextMsgNoToProcess(), 4);
			
			//also check that the sequence has an out of order gap that contains msg 2			
			assertNotNull(rMDBean.getOutOfOrderRanges());
			RangeString rangeString = rMDBean.getOutOfOrderRanges();
			assertTrue(rangeString.isMessageNumberInRanges(2));
			t.commit();
			
			//we deliver msg 2
			//set highest out msg number to 1
			String internalSequenceId = SandeshaUtil.getInternalSequenceID(to, sequenceKey);
			t = mgr.getTransaction();
			RMSBean rmsBean = SandeshaUtil.getRMSBeanFromInternalSequenceId(mgr, internalSequenceId);
			rmsBean.setNextMessageNumber(1);
			// Update the bean
			mgr.getRMSBeanMgr().update(rmsBean);
			t.commit();
			
			clientOptions.setProperty(SandeshaClientConstants.MESSAGE_NUMBER,new Long(2));
			serviceClient.fireAndForget(getPingOMBlock("ping2"));
		}
		finally{
			configContext.getListenerManager().stop();
			serviceClient.cleanup();			
		}

	}
	
	public void testForceInvokeWithDiscardGaps () throws AxisFault  {
		
		String to = "http://127.0.0.1:" + serverPort + "/axis2/services/RMSampleService";
		
		String repoPath = "target" + File.separator + "repos" + File.separator + "client";
		String axis2_xml = "target" + File.separator + "repos" + File.separator + "client" + File.separator + "client_axis2.xml";

		ConfigurationContext configContext = ConfigurationContextFactory.createConfigurationContextFromFileSystem(repoPath,axis2_xml);

		Options clientOptions = new Options ();
		clientOptions.setAction(pingAction);
		clientOptions.setSoapVersionURI(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
		
		clientOptions.setTo(new EndpointReference (to));
		
		String sequenceKey = "sequence2";
		clientOptions.setProperty(SandeshaClientConstants.SEQUENCE_KEY,sequenceKey);
		ServiceClient serviceClient = new ServiceClient (configContext,null);
		serviceClient.setOptions(clientOptions);
		try
		{
			serviceClient.fireAndForget(getPingOMBlock("ping1"));		
			
			//now deliver the next out of order 
			clientOptions.setProperty(SandeshaClientConstants.MESSAGE_NUMBER,new Long(3));
			serviceClient.fireAndForget(getPingOMBlock("ping3"));
	
			String internalSequenceId = SandeshaUtil.getInternalSequenceID(to, sequenceKey);
			waitForMessageToBeAcked(serviceClient, internalSequenceId);
			
			StorageManager mgr = SandeshaUtil.getInMemoryStorageManager(configContext);
			Transaction t = mgr.getTransaction();
			String inboundSequenceID = SandeshaUtil.getSequenceIDFromInternalSequenceID(internalSequenceId,
					mgr);
			t.commit();
			
			SandeshaClient.forceDispatchOfInboundMessages(serverConfigCtx, inboundSequenceID, false);
			
			//check that the server is now expecting msg 4
			StorageManager serverMgr = SandeshaUtil.getInMemoryStorageManager(serverConfigCtx);
			t = serverMgr.getTransaction();
			RMDBean rMDBean = serverMgr.getRMDBeanMgr().retrieve(inboundSequenceID);
			assertNotNull(rMDBean);
			assertEquals(rMDBean.getNextMsgNoToProcess(), 4);
			t.commit();
	  }
		finally{
			configContext.getListenerManager().stop();
			serviceClient.cleanup();			
		}

	}
	
  /**
   * Waits for the maximum of "waittime" for a message to be acked, before returning control to the application.
   * @throws SandeshaException 
   */
  private void waitForMessageToBeAcked(ServiceClient serviceClient, String internalSequenceId) throws SandeshaException
  {
    // Get the highest out message number
    ConfigurationContext context = serviceClient.getServiceContext().getConfigurationContext();
    StorageManager storageManager = SandeshaUtil.getSandeshaStorageManager(context, context.getAxisConfiguration());
    
    // Get a transaction for the property finding
    Transaction transaction = storageManager.getTransaction();
    
    // Get the highest out message property
    RMSBean rmsBean = SandeshaUtil.getRMSBeanFromInternalSequenceId(storageManager, internalSequenceId);
    
    transaction.commit();
    
    long highestOutMsgNum = rmsBean.getHighestOutMessageNumber();
    Long highestOutMsgKey = new Long(highestOutMsgNum);
    
    long timeNow = System.currentTimeMillis();
    long timeToComplete = timeNow + waitTime;
    boolean complete = false;    
    
    while (!complete && timeNow < timeToComplete)
    {
      timeNow = System.currentTimeMillis();

      try
      {                              
        SequenceReport sequenceReport = SandeshaClient.getOutgoingSequenceReport(serviceClient);
        
        if (sequenceReport.getCompletedMessages().contains(highestOutMsgKey))
          complete = true;
        else
          Thread.sleep(tickTime);
  
      }
      catch (Exception e)
      {
        // Ignore
      }
    }
  }
}
