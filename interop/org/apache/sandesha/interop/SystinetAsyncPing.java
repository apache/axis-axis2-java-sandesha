package org.apache.sandesha.interop;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.XMLType;
import org.apache.sandesha.Constants;
import org.apache.sandesha.SandeshaContext;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;

/**
 * Test client for Ping scenario for Systinet.
 *
 * @author Jaliya Ekanyake
 */
public class SystinetAsyncPing {

    private static String targetURL = "http://127.0.0.1:6064/Service";
    private static String sourceHost = "192.248.18.51"; //Change this to your public IP address
    private static String sourcePort = "9070"; //Change this according to the listening port of the TCPMonitor in the
    //client side.

    public static void main(String[] args) {
        System.out.println("Client started...... Synchronous ");
        try {

            Service service = new Service();
            Call call = (Call) service.createCall();

            SandeshaContext ctx = new SandeshaContext();

            ctx.setToURL("http://soap.systinet.net:6064/Service");
            ctx.setAcksToURL("http://" + sourceHost + ":" + sourcePort + "/axis/services/RMService");
            ctx.setFromURL("http://" + sourceHost + ":" + sourcePort + "/axis/services/RMService");
            ctx.setFaultToURL("http://" + sourceHost + ":" + sourcePort + "/axis/services/RMService");

            ctx.initCall(call, targetURL, "urn:wsrm:Ping", Constants.ClientProperties.IN_ONLY);

            call.setOperationName(new QName("http://tempuri.org/", "Ping"));

            call.addParameter("arg1", XMLType.XSD_STRING, ParameterMode.IN);

            call.invoke(new Object[]{"Ping Message Number One"});

            call.invoke(new Object[]{"Ping Message Number Two"});
            ctx.setLastMessage(call);
            call.invoke(new Object[]{"Ping Message Number Three"});

            ctx.endSequence();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}