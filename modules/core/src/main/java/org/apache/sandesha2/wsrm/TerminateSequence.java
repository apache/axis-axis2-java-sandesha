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

package org.apache.sandesha2.wsrm;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;

/**
 * Adds the Terminate Sequence body part.
 */

public class TerminateSequence implements IOMRMPart {

	private Identifier identifier;
	private LastMessageNumber lastMessageNumber;
	private String namespaceValue = null;
	
	public TerminateSequence(String namespaceValue) throws SandeshaException {
		if (!isNamespaceSupported(namespaceValue))
			throw new SandeshaException (SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.unknownSpec,
					namespaceValue));
		
		this.namespaceValue = namespaceValue;
	}

	public String getNamespaceValue() {
		return namespaceValue;
	}

	public Object fromOMElement(OMElement body) throws OMException,SandeshaException {

		if (!(body instanceof SOAPBody))
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.terminateSeqCannotBeAddedToNonBody));

		OMElement terminateSeqPart = body.getFirstChildWithName(new QName(
				namespaceValue, Sandesha2Constants.WSRM_COMMON.TERMINATE_SEQUENCE));

		if (terminateSeqPart == null)
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.noTerminateSeqInElement,
					body.toString()));

		identifier = new Identifier(namespaceValue);
		OMElement identifierPart = terminateSeqPart.getFirstChildWithName(new QName(namespaceValue, Sandesha2Constants.WSRM_COMMON.IDENTIFIER));
		if(identifierPart != null){
			identifier.fromOMElement(identifierPart);
		}
		
		OMElement lastMessageNumberPart = terminateSeqPart.getFirstChildWithName(new QName(namespaceValue, Sandesha2Constants.WSRM_COMMON.LAST_MSG_NUMBER));
		if(lastMessageNumberPart != null){
			lastMessageNumber = new LastMessageNumber(namespaceValue);
			lastMessageNumber.fromOMElement(lastMessageNumberPart);
		}

		return this;
	}

	public OMElement toOMElement(OMElement body) throws OMException {

		if (body == null || !(body instanceof SOAPBody))
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.terminateSeqCannotBeAddedToNonBody));

		if (identifier == null)
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.nullMsgId));

		OMFactory factory= body.getOMFactory();
		
		OMNamespace rmNamespace = factory.createOMNamespace(namespaceValue,Sandesha2Constants.WSRM_COMMON.NS_PREFIX_RM);
		OMElement terminateSequenceElement = factory.createOMElement(
				Sandesha2Constants.WSRM_COMMON.TERMINATE_SEQUENCE, rmNamespace);
		
		identifier.toOMElement(terminateSequenceElement, rmNamespace);
		
		if(lastMessageNumber!=null){
			lastMessageNumber.toOMElement(terminateSequenceElement, rmNamespace);
		}
		
		body.addChild(terminateSequenceElement);
		return body;
	}

	public Identifier getIdentifier() {
		return identifier;
	}

	public void setIdentifier(Identifier identifier) {
		this.identifier = identifier;
	}
	
	public void setLastMessageNumber(LastMessageNumber number){
		this.lastMessageNumber = number;
	}
	
	public LastMessageNumber getLastMessageNumber(){
		return this.lastMessageNumber;
	}

	public void toSOAPEnvelope(SOAPEnvelope envelope) {
		SOAPBody body = envelope.getBody();
		
		//detach if already exist.
		OMElement elem = body.getFirstChildWithName(new QName(namespaceValue,
				Sandesha2Constants.WSRM_COMMON.TERMINATE_SEQUENCE));
		if (elem!=null)
			elem.detach();
		
		toOMElement(body);
	}
	
	public boolean isNamespaceSupported (String namespaceName) {
		if (Sandesha2Constants.SPEC_2005_02.NS_URI.equals(namespaceName))
			return true;
		
		if (Sandesha2Constants.SPEC_2007_02.NS_URI.equals(namespaceName))
			return true;
		
		return false;
	}
	
	public static boolean isLastMsgNumberRequired(String namespaceName){
		
		if (Sandesha2Constants.SPEC_2007_02.NS_URI.equals(namespaceName))
			return true;
		
		return false;		
	}
	
}
