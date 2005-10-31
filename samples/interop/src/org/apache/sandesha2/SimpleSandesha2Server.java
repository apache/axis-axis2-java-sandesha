/*
 * Copyright 2004,2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.sandesha2;

import org.apache.axis2.AxisFault;
import org.apache.axis2.transport.http.SimpleHTTPServer;

public class SimpleSandesha2Server {

	private static String SANDESHA_HOME = "<SANDESHA_HOME>"; //Change this to ur path.
	
	private static String AXIS2_SERVER_PATH = SANDESHA_HOME + "\\target\\server\\";   //this will be available after a maven build
	
	public static void main(String[] args) throws AxisFault {
		System.out.println("Starting sandesha2 server...");
		SimpleHTTPServer server = new SimpleHTTPServer (AXIS2_SERVER_PATH,8080);
		server.start();
	}
}