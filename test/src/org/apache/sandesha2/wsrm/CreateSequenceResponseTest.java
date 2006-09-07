/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
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

package org.apache.sandesha2.wsrm;

import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.SandeshaTestCase;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;

import javax.xml.namespace.QName;

public class CreateSequenceResponseTest extends SandeshaTestCase {

	SOAPFactory factory = OMAbstractFactory.getSOAP11Factory();
	String rmNamespaceValue = Sandesha2Constants.SPEC_2005_02.NS_URI;
	String addressingNamespaceValue = AddressingConstants.Final.WSA_NAMESPACE;
	
    public CreateSequenceResponseTest() {
        super("CreateSequenceResponseTest");

    }

    public void testFromOMElement() throws SandeshaException {
        CreateSequenceResponse res = new CreateSequenceResponse(rmNamespaceValue,addressingNamespaceValue);
        SOAPEnvelope env = getSOAPEnvelope("", "CreateSequenceResponse.xml");
        res.fromOMElement(env.getBody());

        Identifier identifier = res.getIdentifier();
        assertEquals("uuid:88754b00-161a-11da-b6d6-8198de3c47c5", identifier.getIdentifier());

        Accept accept = res.getAccept();
        AcksTo  acksTo = accept.getAcksTo();
        Address address = acksTo.getAddress();
        assertEquals("http://localhost:8070/axis/services/TestService", address.getEpr().getAddress());

    }

    public void testToSOAPEnvelope()  throws SandeshaException {
        CreateSequenceResponse res = new CreateSequenceResponse(rmNamespaceValue,addressingNamespaceValue);

        Identifier identifier = new Identifier(rmNamespaceValue);
        identifier.setIndentifer("uuid:88754b00-161a-11da-b6d6-8198de3c47c5");
        res.setIdentifier(identifier);

        Accept accept = new Accept(rmNamespaceValue,addressingNamespaceValue);
        AcksTo acksTo = new AcksTo(rmNamespaceValue,addressingNamespaceValue);
        Address address = new Address(addressingNamespaceValue);
        address.setEpr(new EndpointReference("http://localhost:8070/axis/services/TestService"));
        acksTo.setAddress(address);
        accept.setAcksTo(acksTo);
        res.setAccept(accept);

        SOAPEnvelope env = getEmptySOAPEnvelope();
        res.toSOAPEnvelope(env);

        OMElement createSeqResponsePart = env.getBody().getFirstChildWithName(
                new QName(rmNamespaceValue, Sandesha2Constants.WSRM_COMMON.CREATE_SEQUENCE_RESPONSE));
        OMElement identifierPart = createSeqResponsePart.getFirstChildWithName(
                new QName(rmNamespaceValue, Sandesha2Constants.WSRM_COMMON.IDENTIFIER));
        assertEquals("uuid:88754b00-161a-11da-b6d6-8198de3c47c5", identifierPart.getText());

        OMElement acceptPart = createSeqResponsePart.getFirstChildWithName(
                new QName(rmNamespaceValue, Sandesha2Constants.WSRM_COMMON.ACCEPT));
        OMElement acksToPart = acceptPart.getFirstChildWithName(
                new QName(rmNamespaceValue, Sandesha2Constants.WSRM_COMMON.ACKS_TO));
        OMElement addressPart = acksToPart.getFirstChildWithName(new QName(
				addressingNamespaceValue, Sandesha2Constants.WSA.ADDRESS));
        assertEquals("http://localhost:8070/axis/services/TestService", addressPart.getText());
    }
}
