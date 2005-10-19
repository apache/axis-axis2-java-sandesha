/*
 * Created on Sep 1, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.apache.sandesha2.wsrm;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.om.OMAbstractFactory;
import org.apache.axis2.om.OMElement;
import org.apache.axis2.om.OMException;
import org.apache.axis2.om.OMNamespace;
import org.apache.sandesha2.Constants;
import org.apache.sandesha2.SOAPAbstractFactory;

/**
 * @author Saminda
 * @author chamikara
 * @author sanka
 */

public class Address implements IOMRMElement {

	EndpointReference epr = null;

	OMElement addressElement;

	OMNamespace rmNamespace = SOAPAbstractFactory.getSOAPFactory(
			Constants.SOAPVersion.DEFAULT)
			.createOMNamespace(Constants.WSA.NS_URI_ADDRESSING,
					Constants.WSA.NS_PREFIX_ADDRESSING);

	public Address() {
		addressElement = SOAPAbstractFactory.getSOAPFactory(
				Constants.SOAPVersion.DEFAULT).createOMElement(
				Constants.WSA.ADDRESS, rmNamespace);
	}
	
	public Address (EndpointReference epr) {
		this();
		this.epr = epr;
	}

	public Object fromOMElement(OMElement element) throws OMException {

		OMElement addressPart = element.getFirstChildWithName(new QName(
				Constants.WSA.NS_URI_ADDRESSING, Constants.WSA.ADDRESS));
		if (addressPart == null)
			throw new OMException(
					"Cant find an Address element in the given part");
		String addressText = addressPart.getText();
		if (addressText == null || addressText == "")
			throw new OMException(
					"Passed element does not have a valid address text");

		addressElement = addressPart;
		epr = new EndpointReference(addressText);
		addressElement = SOAPAbstractFactory.getSOAPFactory(
				Constants.SOAPVersion.DEFAULT).createOMElement(
				Constants.WSA.ADDRESS, rmNamespace);
		return this;

	}

	public OMElement getOMElement() throws OMException {
		return addressElement;
	}

	public OMElement toOMElement(OMElement element) throws OMException {
		if (addressElement == null)
			throw new OMException(
					"Cant set Address. The address element is null");

		if (epr == null || epr.getAddress() == null || epr.getAddress() == "")
			throw new OMException(
					"cant set the address. The address value is not valid");

		addressElement.setText(epr.getAddress());
		element.addChild(addressElement);

		addressElement = SOAPAbstractFactory.getSOAPFactory(
				Constants.SOAPVersion.DEFAULT).createOMElement(
				Constants.WSA.ADDRESS, rmNamespace);

		return element;
	}

	public EndpointReference getEpr() {
		return epr;
	}

	public void setEpr(EndpointReference epr) {
		this.epr = epr;
	}
}