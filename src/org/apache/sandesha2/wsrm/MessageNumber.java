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
package org.apache.sandesha2.wsrm;

import javax.xml.namespace.QName;

import org.apache.ws.commons.om.OMElement;
import org.apache.ws.commons.om.OMException;
import org.apache.ws.commons.om.OMNamespace;
import org.apache.ws.commons.soap.SOAPFactory;
import org.apache.sandesha2.Sandesha2Constants;

/**
 * @author Chamikara Jayalath <chamikaramj@gmail.com>
 * @author Sanka Samaranayaka <ssanka@gmail.com>
 * @author Saminda Abeyruwan  <saminda@opensource.lk>
 */

public class MessageNumber implements IOMRMElement {
	
	private long messageNumber;
	private OMElement messageNoElement;
	
	OMNamespace rmNamespace = null;
	
	SOAPFactory factory;
	
	public MessageNumber(SOAPFactory factory){
		this.factory = factory;
	    rmNamespace = factory.createOMNamespace(Sandesha2Constants.WSRM.NS_URI_RM, Sandesha2Constants.WSRM.NS_PREFIX_RM);
		messageNoElement = factory.createOMElement(Sandesha2Constants.WSRM.MSG_NUMBER,rmNamespace);
	}
	
	public long getMessageNumber(){
		return messageNumber;
	}
	public void setMessageNumber(long messageNumber){
		this.messageNumber = messageNumber;
	}
	
	public Object fromOMElement(OMElement seqenceElement) throws OMException {
		OMElement msgNumberPart = seqenceElement.getFirstChildWithName( 
				new QName (Sandesha2Constants.WSRM.NS_URI_RM,Sandesha2Constants.WSRM.MSG_NUMBER));
		if (msgNumberPart==null)
			throw new OMException ("The passed sequnce element does not contain a message number part");
		
		messageNoElement = factory.createOMElement(Sandesha2Constants.WSRM.MSG_NUMBER,rmNamespace);

		String msgNoStr = msgNumberPart.getText();
		messageNumber = Long.parseLong(msgNoStr);
		return this;
	}
	
	public OMElement toOMElement(OMElement element) throws OMException {
		if (messageNumber <= 0 ){
			throw new OMException("Set A Valid Message Number");
		}
		
		messageNoElement.setText(Long.toString(messageNumber));
		element.addChild(messageNoElement);
		
		messageNoElement = factory.createOMElement(Sandesha2Constants.WSRM.MSG_NUMBER,rmNamespace);
		
		return element;
	}
	
	public OMElement getOMElement() throws OMException {
		return messageNoElement;
	}
	
	public OMElement getMessageNumberElement(){
		return messageNoElement;
	}


}
