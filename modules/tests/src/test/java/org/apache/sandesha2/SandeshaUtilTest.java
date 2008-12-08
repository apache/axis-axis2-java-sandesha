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

package org.apache.sandesha2;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.util.MessageContextBuilder;
import org.apache.sandesha2.util.Range;
import org.apache.sandesha2.util.RangeString;
import org.apache.sandesha2.util.SandeshaUtil;

import junit.framework.TestCase;

public class SandeshaUtilTest extends TestCase {

	public void testUUIDGen () {
		String UUID1 = SandeshaUtil.getUUID();
		String UUID2 = SandeshaUtil.getUUID();
		
		assertTrue(UUID1!=null);
		assertTrue(UUID2!=null);
		assertTrue(!UUID1.equals(UUID2));
		
		assertTrue(UUID1.startsWith("urn:uuid:"));
		assertTrue(UUID2.startsWith("urn:uuid:"));
	}
	
	public void testInternalSequenceIDToSequenceKeyConversion() {
		String toEPR = "http://127.0.0.1:1111/some_random_uri";
		String sequenceKey = "1234abcd";
		
		String internalSequenceID = SandeshaUtil.getInternalSequenceID(toEPR, sequenceKey);
		
		//check that we can parse out the sequence key
		assertEquals(sequenceKey, SandeshaUtil.getSequenceKeyFromInternalSequenceID(internalSequenceID, toEPR));
		
		//try an internal sequenceID without a sequenceKey - should get null
		internalSequenceID = SandeshaUtil.getSequenceKeyFromInternalSequenceID(toEPR, null);
		assertNull(SandeshaUtil.getSequenceKeyFromInternalSequenceID(internalSequenceID, toEPR));
		
		//for badly formed sequences, or for server-side response sequences, check 
		//we just get null
		String outgoingSequenceID = SandeshaUtil.getOutgoingSideInternalSequenceID(SandeshaUtil.getUUID());
		assertNull(SandeshaUtil.getSequenceKeyFromInternalSequenceID(outgoingSequenceID, toEPR));
		
	}
	
	public void testGetAckRangesFromRangeStringOutOfOrder() {
		
		RangeString rangeString = new RangeString();
		rangeString.addRange(new Range(3));
		rangeString.addRange(new Range(6));
		rangeString.addRange(new Range(1));
		rangeString.addRange(new Range(5));
		rangeString.addRange(new Range(8));
		rangeString.addRange(new Range(2));
		
		ArrayList<Range> list = SandeshaUtil.getAckRangeArrayList(rangeString,Sandesha2Constants.SPEC_2005_02.NS_URI);
		assertNotNull(list);
		assertEquals(list.size(),3);
		
		Iterator<Range> it = list.iterator();
		Range ackRange = null;
		
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,1);
		assertEquals(ackRange.upperValue,3);
		
		ackRange = null;
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,5);
		assertEquals(ackRange.upperValue,6);
		
		ackRange = null;
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,8);
		assertEquals(ackRange.upperValue,8);
		
		assertFalse(it.hasNext());
	}
	
	public void testGetAckRangesFromRangeStringGapFilling () {
		//build a range string to represent the completed messages
		RangeString rangeString = new RangeString();
		rangeString.addRange(new Range(1,3));
		rangeString.addRange(new Range(4));
		//insert a gap - number 5 is missing
		rangeString.addRange(new Range(6));
		//insert a gap - 7 and 8 are missing
		rangeString.addRange(new Range(9, 10));
		
		ArrayList<Range> list = SandeshaUtil.getAckRangeArrayList(rangeString,Sandesha2Constants.SPEC_2005_02.NS_URI);
		assertNotNull(list);
		//we expect 3 ranges: [1-4] [6] [9-10]
		assertEquals(list.size(),3);
		
		Iterator<Range> it = list.iterator();
		Range ackRange = null;
		
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,1);
		assertEquals(ackRange.upperValue,4);
		
		ackRange = null;
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,6);
		assertEquals(ackRange.upperValue,6);
		
		ackRange = null;
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,9);
		assertEquals(ackRange.upperValue,10);
		
		assertFalse(it.hasNext());
		
		//ok, now plug a gap at msg 5
		rangeString.addRange(new Range(5));
		list = SandeshaUtil.getAckRangeArrayList(rangeString,Sandesha2Constants.SPEC_2005_02.NS_URI);
		assertNotNull(list);
		//we expect 2 ranges: [1-6] [9-10]
		it = list.iterator();
		ackRange = null;
		
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,1);
		assertEquals(ackRange.upperValue,6);
		
		ackRange = null;
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,9);
		assertEquals(ackRange.upperValue,10);
		
		assertFalse(it.hasNext());
		
		//plug all of the gaps - 7 and 8
		rangeString.addRange(new Range(8));
		rangeString.addRange(new Range(7,8)); 
		list = SandeshaUtil.getAckRangeArrayList(rangeString,Sandesha2Constants.SPEC_2005_02.NS_URI);
		assertNotNull(list);
		//we expect 1 ranges: [1-10]
		it = list.iterator();
		ackRange = null;
		
		ackRange = (Range) it.next();
		assertNotNull(ackRange);
		assertEquals(ackRange.lowerValue,1);
		assertEquals(ackRange.upperValue,10);
		
		assertFalse(it.hasNext());
	}
	
	/**
	 * Checks that a Fault message can be created from an empty MessageContext
	 * 
	 * @throws Exception
	 */
	public void testCreateFaultMessageContext() throws Exception {
		
		String repoPath = "target" + File.separator + "repos" + File.separator + "client";
		String axis2_xml = "target" + File.separator + "repos" + File.separator + "client" + File.separator + "client_axis2.xml";
		ConfigurationContext configContext = ConfigurationContextFactory.createConfigurationContextFromFileSystem(repoPath,axis2_xml);
		MessageContext messageContext = new MessageContext();
		messageContext.setConfigurationContext(configContext);
		messageContext = MessageContextBuilder.createFaultMessageContext(messageContext, new Exception());
	}

	
	
	
}
