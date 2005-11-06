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

/**
 * @author Saminda
 * @author chamikara
 * @author sanka
 */

import javax.xml.namespace.QName;

import org.apache.axis2.om.OMElement;
import org.apache.axis2.om.OMException;
import org.apache.axis2.om.OMNamespace;
import org.apache.axis2.soap.SOAPFactory;
import org.apache.sandesha2.Constants;
import org.apache.sandesha2.util.SOAPAbstractFactory;

public class Identifier implements Constants, IOMRMElement {

	private OMElement identifierElement;

	private String identifier = null;

	OMNamespace wsrmNamespace = null;

	private SOAPFactory factory;
	
	public Identifier(SOAPFactory factory) {
		this.factory = factory;
		wsrmNamespace = factory.createOMNamespace(
				Constants.WSRM.NS_URI_RM, Constants.WSRM.NS_PREFIX_RM);
		identifierElement = factory.createOMElement(
				Constants.WSRM.IDENTIFIER, wsrmNamespace);
	}

	public void setIndentifer(String identifier) {
		this.identifier = identifier;
	}

	public String getIdentifier() {
		return identifier;
	}

	public OMElement getOMElement() throws OMException {
		return identifierElement;
	}

	public Object fromOMElement(OMElement element) throws OMException {
		
		OMElement identifierPart = element.getFirstChildWithName(new QName(
				Constants.WSRM.NS_URI_RM, Constants.WSRM.IDENTIFIER));
		if (identifierPart == null)
			throw new OMException(
					"The parsed element does not contain an identifier part");
		identifierElement = factory.createOMElement(
				Constants.WSRM.IDENTIFIER, wsrmNamespace);

		String identifierText = identifierPart.getText();
		if (identifierText == null || identifierText == "")
			throw new OMException("The identifier value is not valid");

		identifier = identifierText;
		return this;
	}

	public OMElement toOMElement(OMElement element) throws OMException {

		if (identifier == null || identifier == "") {
			throw new OMException("identifier is not set .. ");
		}

		identifierElement.setText(identifier);
		element.addChild(identifierElement);

		identifierElement = factory.createOMElement(
				Constants.WSRM.IDENTIFIER, wsrmNamespace);

		return element;
	}

	public String toString() {
		return identifier;
	}

	public int hashCode() {
		return identifier.hashCode();
	}
}