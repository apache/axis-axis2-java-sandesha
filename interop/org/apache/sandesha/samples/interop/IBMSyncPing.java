package org.apache.sandesha.samples.interop;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.XMLType;
import org.apache.sandesha.Constants;
import org.apache.sandesha.RMInitiator;
import org.apache.sandesha.RMTransport;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;

public class IBMSyncPing {

    private static String targetURL = "http://127.0.0.1:8080/wsrm/services/rmDemos";

    public static void main(String[] args) {
        System.out.println("Client started...... Synchronous Ping - IBM");
        try {

            RMInitiator.initClient(true);

            Service service = new Service();
            Call call = (Call) service.createCall();

            call.setProperty(Constants.ClientProperties.SYNC, new Boolean(true));
            call.setProperty(Constants.ClientProperties.ACTION, "urn:wsrm:Ping");
            call.setProperty(Constants.ClientProperties.TO, "http://wsi.alphaworks.ibm.com:8080/wsrm/services/rmDemos");

            call.setProperty(Constants.ClientProperties.ACKS_TO, Constants.WSA.NS_ADDRESSING_ANONYMOUS);

            call.setTargetEndpointAddress(targetURL);
            call.setOperationName(new QName("http://tempuri.org/", "Ping"));
            call.setTransport(new RMTransport(targetURL, ""));

            call.addParameter("Text", XMLType.XSD_STRING, ParameterMode.IN);

            //First Message
            call.setProperty(Constants.ClientProperties.MSG_NUMBER, new Long(1));
            call.invoke(new Object[]{"Ping Message Number One"});

            //Second Message
            call.setProperty(Constants.ClientProperties.MSG_NUMBER, new Long(2));
            call.invoke(new Object[]{"Ping Message Number Two"});

            //Third Message
            call.setProperty(Constants.ClientProperties.MSG_NUMBER, new Long(3));
            call.setProperty(Constants.ClientProperties.LAST_MESSAGE, new Boolean(true)); //For last message.
            call.invoke(new Object[]{"Ping Message Number Three"});

            RMInitiator.stopClient();

        } catch (Exception e) {
            //System.err.println(e.toString());
            e.printStackTrace();
        }
    }
}