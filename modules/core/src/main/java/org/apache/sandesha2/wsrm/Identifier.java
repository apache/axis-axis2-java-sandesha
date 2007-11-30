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

/**
 * Represents an Identifier element.
 */

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;

public class Identifier {

	private String identifier = null;
	
	public Identifier(String namespaceValue) throws SandeshaException {
		if (!isNamespaceSupported(namespaceValue))
			throw new SandeshaException (SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.unknownSpec,
					namespaceValue));
	}

	public void setIndentifer(String identifier) {
		this.identifier = identifier;
	}

	public String getIdentifier() {
		return identifier;
	}

	public Object fromOMElement(OMElement identifierPart) throws OMException {
		if (identifierPart == null)
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.invalidIdentifier,
					null));
		
		String identifierText = identifierPart.getText();
		if (identifierText == null || identifierText == "")
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.invalidIdentifier,
					identifierPart.toString()));

		identifier = identifierText;
		return this;
	}
	
	public OMElement toOMElement(OMElement element, OMNamespace wsrmNamespace) throws OMException {

		if (identifier == null || identifier == "") {
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.invalidIdentifier,
					element.toString()));
		}
		
		OMFactory factory = element.getOMFactory();
		OMElement identifierElement = factory.createOMElement(Sandesha2Constants.WSRM_COMMON.IDENTIFIER, wsrmNamespace);

		identifierElement.setText(identifier);
		element.addChild(identifierElement);

		return element;
	}

	public String toString() {
		return identifier;
	}

	public int hashCode() {
		return identifier.hashCode();
	}
	
	public boolean isNamespaceSupported (String namespaceName) {
		if (Sandesha2Constants.SPEC_2005_02.NS_URI.equals(namespaceName))
			return true;
		
		if (Sandesha2Constants.SPEC_2007_02.NS_URI.equals(namespaceName))
			return true;
		
		return false;
	}
}
