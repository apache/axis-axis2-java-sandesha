/*
 * Copyright 2004,2005 The Apache Software Foundation.
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
 */

package org.apache.sandesha2.wsrm_2006_02;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.namespace.QName;

import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.context.MessageContextConstants;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.client.Sandesha2ClientAPI;
import org.apache.sandesha2.client.reports.SequenceReport;
import org.apache.ws.commons.om.OMAbstractFactory;
import org.apache.ws.commons.om.OMElement;
import org.apache.ws.commons.om.OMFactory;
import org.apache.ws.commons.om.OMNamespace;
import org.apache.ws.commons.soap.SOAP12Constants;

/**
 * @author Chamikara Jayalath <chamikaramj@gmail.com>
 */
public class Scenario_1_1 {

	private static final String applicationNamespaceName = "http://tempuri.org/"; 
	private static final String Ping = "Ping";
	private static final String Text = "Text";
	
	private static String toIP = "127.0.0.1";
	private static String toPort = "9762";
	private static String transportToIP = "127.0.0.1";
	private static String transportToPort = "8070";
	private static String servicePart = "/axis2/services/RMInteropService";
	private static String toEPR = "http://" + toIP +  ":" + toPort + servicePart;
	private static String transportToEPR = "http://" + transportToIP +  ":" + transportToPort + servicePart;
	
	private static String SANDESHA2_HOME = "<SANDESHA2_HOME>"; //Change this to ur path.
	
	private static String AXIS2_CLIENT_PATH = SANDESHA2_HOME + File.separator + "target" + File.separator +"repos" + File.separator + "client" + File.separator;   //this will be available after a maven build
	
	public static void main(String[] args) throws AxisFault,IOException  {
		
		String axisClientRepo = null;
		if (args!=null && args.length>0)
			axisClientRepo = args[0];
		
		if (axisClientRepo!=null && !"".equals(axisClientRepo)) {
			AXIS2_CLIENT_PATH = axisClientRepo;
			SANDESHA2_HOME = "";
		}
		
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("sandesha2_interop.properties");

		Properties properties = new Properties();
		if (in != null) {
			properties.load(in);
		}
		
		toEPR = properties.getProperty("to");
		transportToEPR = properties.getProperty("transportTo");

		new Scenario_1_1 ().run();
	}
	
	private void run () throws AxisFault {
		
		if ("<SANDESHA2_HOME>".equals(SANDESHA2_HOME)){
			System.out.println("ERROR: Please change <SANDESHA2_HOME> to your Sandesha2 installation directory.");
			return;
		}
		
		String axis2_xml = AXIS2_CLIENT_PATH + "axis2.xml";
		ConfigurationContext configContext = ConfigurationContextFactory.createConfigurationContextFromFileSystem(AXIS2_CLIENT_PATH,axis2_xml);
		Options clientOptions = new Options ();
		clientOptions.setProperty(MessageContextConstants.TRANSPORT_URL,transportToEPR);
		clientOptions.setProperty(Options.COPY_PROPERTIES, new Boolean (true));
		clientOptions.setTo(new EndpointReference (toEPR));
		
		String sequenceKey = "sequence1";
		clientOptions.setProperty(Sandesha2ClientAPI.SEQUENCE_KEY,sequenceKey);
	    
//		clientOptions.setProperty(MessageContextConstants.CHUNKED,Constants.VALUE_FALSE);   //uncomment this to send messages without chunking.
		
		clientOptions.setSoapVersionURI(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI);   //uncomment this to send messages in SOAP 1.2
		
		clientOptions.setProperty(Sandesha2ClientAPI.RM_SPEC_VERSION,Sandesha2Constants.SPEC_VERSIONS.WSRX);  //uncomment this to send the messages according to the WSRX spec.
		
		ServiceClient serviceClient = new ServiceClient (configContext,null);		
		
		serviceClient.engageModule(new QName ("sandesha2"));  //engaging the sandesha2 module.
		
		serviceClient.setOptions(clientOptions);
		
		serviceClient.fireAndForget(getPingOMBlock("ping1"));
		serviceClient.fireAndForget(getPingOMBlock("ping2"));
		serviceClient.fireAndForget(getPingOMBlock("ping3"));
		
		SequenceReport sequenceReport = null;		
		boolean complete = false;
		while (!complete) {
			sequenceReport = Sandesha2ClientAPI.getOutgoingSequenceReport(toEPR,sequenceKey,configContext);
			if (sequenceReport!=null && sequenceReport.getCompletedMessages().size()==3) 
				complete = true;
			else {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
	    		}
			}
		} 		
		
		Sandesha2ClientAPI.terminateSequence(toEPR,sequenceKey,serviceClient,configContext);
		serviceClient.finalizeInvoke();
	}
	
	private static OMElement getPingOMBlock(String text) {
		OMFactory fac = OMAbstractFactory.getOMFactory();
		OMNamespace namespace = fac.createOMNamespace(applicationNamespaceName,"ns1");
		OMElement pingElem = fac.createOMElement(Ping, namespace);
		OMElement textElem = fac.createOMElement(Text, namespace);
		
		textElem.setText(text);
		pingElem.addChild(textElem);

		return pingElem;
	}
	
}
