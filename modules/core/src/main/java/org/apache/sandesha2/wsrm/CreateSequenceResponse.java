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
import org.apache.axis2.AxisFault;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;

/**
 * Adds the CreateSequenceResponse body part.
 */

public class CreateSequenceResponse implements IOMRMPart {
	
	private Identifier identifier;
	
	private Accept accept;
	
	private Expires expires;
	
	private String rmNamespaceValue = null;
	
	public CreateSequenceResponse(String rmNamespaceValue) throws SandeshaException {
		if (!isNamespaceSupported(rmNamespaceValue))
			throw new SandeshaException (SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.unknownSpec,
					rmNamespaceValue));
		
		this.rmNamespaceValue = rmNamespaceValue;
	}

	public String getNamespaceValue() {
		return rmNamespaceValue;
	}

	public Object fromOMElement(OMElement bodyElement) throws OMException,AxisFault {

		if (bodyElement == null || !(bodyElement instanceof SOAPBody))
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.createSeqResponseCannotBeAddedToNonBody));

		SOAPBody SOAPBody = (SOAPBody) bodyElement;

		OMElement createSeqResponsePart = SOAPBody
				.getFirstChildWithName(new QName(rmNamespaceValue,Sandesha2Constants.WSRM_COMMON.CREATE_SEQUENCE_RESPONSE));
		if (createSeqResponsePart == null)
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.noCreateSeqResponsePartInElement,
					bodyElement.toString()));

		identifier = new Identifier(rmNamespaceValue);
		OMElement identifierPart = createSeqResponsePart.getFirstChildWithName(new QName(rmNamespaceValue, Sandesha2Constants.WSRM_COMMON.IDENTIFIER));
		if(identifierPart != null){
			identifier.fromOMElement(identifierPart);
		}

		OMElement expiresPart = createSeqResponsePart.getFirstChildWithName(
					new QName(rmNamespaceValue,
					Sandesha2Constants.WSRM_COMMON.EXPIRES));
		if (expiresPart != null) {
			expires = new Expires(rmNamespaceValue);
			expires.fromOMElement(createSeqResponsePart);
		}

		OMElement acceptPart = createSeqResponsePart.getFirstChildWithName(
						new QName(rmNamespaceValue,
						Sandesha2Constants.WSRM_COMMON.ACCEPT));
		if (acceptPart != null) {
			accept = new Accept(rmNamespaceValue);
			accept.fromOMElement(createSeqResponsePart);
		}

		return this;
	}

	public OMElement toOMElement(OMElement bodyElement) throws OMException, AxisFault {

		if (bodyElement == null || !(bodyElement instanceof SOAPBody))
			throw new OMException(
					SandeshaMessageHelper.getMessage(
							SandeshaMessageKeys.createSeqResponseCannotBeAddedToNonBody));

		SOAPBody SOAPBody = (SOAPBody) bodyElement;

		if (identifier == null)
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.invalidIdentifier,
					bodyElement.toString()));

		OMFactory factory = bodyElement.getOMFactory();
		
		OMNamespace rmNamespace = factory.createOMNamespace(rmNamespaceValue,Sandesha2Constants.WSRM_COMMON.NS_PREFIX_RM);
		
		OMElement createSequenceResponseElement = factory.createOMElement(
				Sandesha2Constants.WSRM_COMMON.CREATE_SEQUENCE_RESPONSE,
				rmNamespace);
		
		identifier.toOMElement(createSequenceResponseElement, rmNamespace);

		if (expires != null) {
			expires.toOMElement(createSequenceResponseElement);
		}

		if (accept != null) {
			accept.toOMElement(createSequenceResponseElement);
		}

		SOAPBody.addChild(createSequenceResponseElement);

		

		return SOAPBody;
	}

	public void setIdentifier(Identifier identifier) {
		this.identifier = identifier;
	}

	public Identifier getIdentifier() {
		return identifier;
	}

	public void setAccept(Accept accept) {
		this.accept = accept;
	}

	public Accept getAccept() {
		return accept;
	}

	public Expires getExpires() {
		return expires;
	}

	public void setExpires(Expires expires) {
		this.expires = expires;
	}

	public void toSOAPEnvelope(SOAPEnvelope envelope) throws AxisFault {
		SOAPBody body = envelope.getBody();
		
		//detach if already exist.
		OMElement elem = body.getFirstChildWithName(new QName(rmNamespaceValue,
				Sandesha2Constants.WSRM_COMMON.CREATE_SEQUENCE_RESPONSE));
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
	
}
