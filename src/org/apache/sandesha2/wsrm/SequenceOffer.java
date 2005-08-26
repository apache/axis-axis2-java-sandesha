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

import org.apache.axis2.om.OMAbstractFactory;
import org.apache.axis2.om.OMElement;
import org.apache.axis2.om.OMException;
import org.apache.axis2.om.OMNamespace;
import org.apache.axis2.soap.SOAPEnvelope;
import org.apache.sandesha2.Constants;

/**
 * @author Saminda
 *
 */
public class SequenceOffer implements IOMRMElement {
	private OMElement sequenceOfferElement;
	private Identifier identifier;
	
	OMNamespace sequenceOfferNameSpace = 
		OMAbstractFactory.getSOAP11Factory().createOMNamespace(
				Constants.WSRM.NS_URI_RM, Constants.WSRM.NS_PREFIX_RM);
	public SequenceOffer(){
		sequenceOfferElement = OMAbstractFactory.getSOAP11Factory().createOMElement(
				Constants.WSRM.SEQUENCE_OFFER,sequenceOfferNameSpace);
	}
	public void addChildElement(OMElement element) throws OMException{
		sequenceOfferElement.addChild(element);
	}
	
	public OMElement getSOAPElement() throws OMException {
		sequenceOfferElement.addChild(identifier.getSOAPElement());
		return sequenceOfferElement;
	}

	public Object fromSOAPEnvelope(SOAPEnvelope envelope) throws OMException {
		identifier = new Identifier();
		identifier.fromSOAPEnvelope(envelope);
		return this;
	}

	public OMElement toSOAPEnvelope(OMElement messageElement) throws OMException {
		if (identifier != null){
			identifier.toSOAPEnvelope(sequenceOfferElement);
		}
		messageElement.addChild(sequenceOfferElement);
		return messageElement;
	}
	public Identifier getIdentifer(){
		return identifier;
	}
	public void setIdentifier(Identifier identifier){
		this.identifier = identifier;		
	}
	

}