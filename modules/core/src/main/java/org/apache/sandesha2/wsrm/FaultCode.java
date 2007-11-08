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
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;

/**
 * Represents an FaultCode element.
 */

public class FaultCode implements IOMRMElement {

	private QName faultCode = null;
	
	private String namespaceValue = null;

	private String detail;
	
	private OMElement detailOMElement;

	private OMElement extendedDetailOMElement;
	
	public FaultCode(String namespaceValue) throws SandeshaException {
		if (!isNamespaceSupported(namespaceValue))
			throw new SandeshaException (SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.unknownSpec,
					namespaceValue));
		
		this.namespaceValue = namespaceValue;
	}

	public String getNamespaceValue() {
		return namespaceValue;
	}

	public Object fromOMElement(OMElement sequenceFault) throws OMException {

		if (sequenceFault == null)
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.noFaultCodePart,
					null));

		OMElement faultCodePart = sequenceFault
				.getFirstChildWithName(new QName(namespaceValue,
						Sandesha2Constants.WSRM_COMMON.FAULT_CODE));

		if (faultCodePart == null)
			throw new OMException(SandeshaMessageHelper.getMessage(
					SandeshaMessageKeys.noFaultCode,
					sequenceFault.toString()));

		this.faultCode = faultCodePart.getTextAsQName();

		OMElement detailPart = sequenceFault
			.getFirstChildWithName(new QName(namespaceValue,
					Sandesha2Constants.WSRM_COMMON.DETAIL));
		
		if (detailPart != null) {
			detailOMElement = detailPart;
			
			OMElement identifier = detailPart
				.getFirstChildWithName(new QName(namespaceValue, 
						Sandesha2Constants.WSRM_COMMON.IDENTIFIER));
			if (identifier != null) {
				detail = identifier.getText();
			}
		}

		return sequenceFault;

	}

	public OMElement toOMElement(OMElement sequenceFault) throws OMException {

		if (sequenceFault == null)
			throw new OMException(
					SandeshaMessageHelper.getMessage(
							SandeshaMessageKeys.nullPassedElement));

		if (faultCode == null)
			throw new OMException(
					SandeshaMessageHelper.getMessage(
							SandeshaMessageKeys.noFaultCode));

		OMFactory factory = sequenceFault.getOMFactory();
		
		OMNamespace rmNamespace = factory.createOMNamespace(namespaceValue,Sandesha2Constants.WSRM_COMMON.NS_PREFIX_RM);
		OMElement faultCodeElement = factory.createOMElement(Sandesha2Constants.WSRM_COMMON.FAULT_CODE,rmNamespace);
		OMElement detailElement = factory.createOMElement(Sandesha2Constants.WSRM_COMMON.DETAIL,rmNamespace);

		faultCodeElement.setText(faultCode);
		sequenceFault.addChild(faultCodeElement);

		if (detailOMElement != null)
			detailElement.addChild(detailOMElement);
		
		if (detail != null)
			detailElement.setText(detail);
		
		if (extendedDetailOMElement != null) {
			detailElement.addChild(extendedDetailOMElement);
		}
		
		sequenceFault.addChild(detailElement);
		
		return sequenceFault;
	}
    
    public void setFaultCode(QName faultCode) {
        this.faultCode = faultCode;
    }
    
    public QName getFaultCode() {
        return faultCode;
    }

  	public void setDetail(String detail) {
  		this.detail = detail; 
    }

  	public String getDetail() {
  		return detail;
  	}

  	public OMElement getDetailOMElement() {
    	return detailOMElement;
    }

  	public void setDetailOMElement(OMElement detailOMElement) {
    	this.detailOMElement = detailOMElement;
    }

  	public void setExtendedDetailOMElement(OMElement detail2) {
    	this.extendedDetailOMElement = detail2;
    }

  	public OMElement getExtendedDetailOMElement() {
    	return extendedDetailOMElement;
    }
 		
	public boolean isNamespaceSupported (String namespaceName) {
		if (Sandesha2Constants.SPEC_2005_02.NS_URI.equals(namespaceName))
			return true;
		
		if (Sandesha2Constants.SPEC_2007_02.NS_URI.equals(namespaceName))
			return true;
		
		return false;
	}


}
