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
package org.apache.sandesha.server;

import javax.xml.rpc.ServiceException;
import javax.xml.soap.SOAPEnvelope;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.sandesha.Constants;
import org.apache.sandesha.EnvelopeCreator;
import org.apache.sandesha.IStorageManager;
import org.apache.sandesha.RMException;
import org.apache.sandesha.RMMessageContext;
import org.apache.sandesha.server.queue.ServerQueue;

/**
 * @author JEkanayake
 *  
 */
public class Sender implements Runnable {
    private IStorageManager storageManager;

    public Sender() {
        storageManager = new ServerStorageManager();
    }

    public Sender(IStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public void run() {

        while (true) {
            long startTime = System.currentTimeMillis();
            boolean hasMessages = true;
            do {
                //System.out.println("SENDER");
                RMMessageContext rmMessageContext = storageManager
                        .getNextMessageToSend();
                if (rmMessageContext == null) {
                    hasMessages = false;
                    //System.out.println("rmMessageContext == null");
                } else {
                    //Send the message.

                    if (rmMessageContext.getMsgContext() == null)
                        System.out
                                .println("rmMessageContext.getMsgContext()  == null");
                    if (rmMessageContext.getMsgContext().getRequestMessage() == null)
                        System.out
                                .println("rmMessageContext.getMsgContext().getRequestMessage()  == null");

                    switch (rmMessageContext.getMessageType()) {
                    case Constants.MSG_TYPE_CREATE_SEQUENCE_REQUEST: {
                        try{
                            sendCreateSequenceRequest(rmMessageContext);
                        }catch(RMException rmEx){
                            //TODO log the error.
                            rmEx.printStackTrace();
                            break;
                        }
                  
                        break;
                    }
                    case Constants.MSG_TYPE_CREATE_SEQUENCE_RESPONSE: {
                        try {
                            //Send creat seq message.
                            //No response and we can just close the connection
                            sendCreateSequenceResponse(rmMessageContext);
                        }catch(RMException rmEx){
                            //TODO log the error.
                            rmEx.printStackTrace();
                            break;
                        }
                        break;
                    }
                    case Constants.MSG_TYPE_TERMINATE_SEQUENCE: {
                        break;
                    }
                    case Constants.MSG_TYPE_ACKNOWLEDGEMENT: {
                        System.out.println("SENDING ASYNC ACK .....\n");
                        try {
                            sendAcknowldgement(rmMessageContext);
                        }catch(RMException rmEx){
                            //TODO log the error.
                            rmEx.printStackTrace();
                            break;
                        }
                        break;
                    }
                    case Constants.MSG_TYPE_SERVICE_REQUEST:{

                        //Send the response message.
                        //Here we need to figure out a mechanism to load the
                        // response handlers
                        //that are scheduled to run in the original response
                        // path.
                        //Need to re-send messsages if we didn't get a
                        // response.
                        //RMMessageContext a field to store the long
                        // lastProcessedTime
                        //Another field to hold retransmission count.
                        System.out.println("SENDING REQUEST MESSAGE .....\n");
                                               
                        SOAPEnvelope requestEnvelope = null;

                        if (rmMessageContext.getReTransmissionCount() <= Constants.MAXIMUM_RETRANSMISSION_COUNT) {
                            if ((System.currentTimeMillis() - rmMessageContext
                                    .getLastPrecessedTime()) > Constants.RETRANSMISSION_INTERVAL) {

                                long msgNo=storageManager.getNextMessageNumber(rmMessageContext.getSequenceID());
                                rmMessageContext.setMsgNumber(msgNo);
                                //TODO
                                //We should do this only once and then need to
                                //Save the respones message.
                                //if (rmMessageContext.getReTransmissionCount()
                                // == 0) {
                                //Need to create the response envelope.
                                requestEnvelope = EnvelopeCreator.createServiceRequestEnvelope(rmMessageContext);
                                rmMessageContext.getMsgContext()
                                        .setRequestMessage(
                                                new Message(requestEnvelope));
                                System.out.println(requestEnvelope);
                                //}

                                //System.out.println(rmMessageContext
                                //        .getAddressingHeaders().getReplyTo()
                                //        .getAddress().toString());

                                try {
                                    Service service = new Service();
                                    Call call = (Call) service.createCall();
                                    call.setTargetEndpointAddress(rmMessageContext.getOutGoingAddress());
                                    
                                    //NOTE: WE USE THE REQUEST MESSAGE TO SEND
                                    // THE RESPONSE.

                                    call.setRequestMessage(rmMessageContext
                                            .getMsgContext()
                                            .getRequestMessage());
                                    //System.out.println(rmMessageContext.getMsgContext().getResponseMessage().getSOAPPartAsString());
                                    try {
                                        rmMessageContext
                                                .setLastPrecessedTime(System
                                                        .currentTimeMillis());
                                        rmMessageContext
                                                .setReTransmissionCount(rmMessageContext
                                                        .getReTransmissionCount() + 1);
                                        System.out
                                                .println("INVOKING THE RESPONSE MESSAGE 88888888888888888888888");
                                        //We are not expecting the ack over the
                                        // same HTTP connection.
                                        call.invoke();
                                        //System.out.println(call.getResponseMessage().getSOAPPartAsString());
                                    } catch (AxisFault e) {
                                        e.printStackTrace();
                                        break;
                                    }

                                } catch (ServiceException e1) {
                                    System.out
                                            .println("(!)(!)(!)Cannot send the Response message.....");
                                    e1.printStackTrace();
                                    break;
                                }
                            }
                            break;
                        }
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        /*try {
                            System.out.println(rmMessageContext.getMsgContext().getRequestMessage().getSOAPPartAsString());
                            
                            Service service = new Service();
                            Call call = (Call) service.createCall();
                            call.setTargetEndpointAddress("http://127.0.0.1:8080/axis/services/EchoStringService?wsdl");
                            Message reqMessage= new Message(rmMessageContext
                                    .getMsgContext()
                                    .getRequestMessage().getSOAPEnvelope());
                            call.setRequestMessage(reqMessage);
                            System.out.println("INVOKING THE RESPONSE MESSAGE 8888888888888888888888");
                                call.invoke();
                                //System.out.println(call.getResponseMessage().getSOAPPartAsString());
                            } catch (Exception e) {
                                e.printStackTrace();
                                break;
                            }
                        break;*/
                    
                    }
                    case Constants.MSG_TYPE_SERVICE_RESPONSE: {
                        //Send the response message.
                        //Here we need to figure out a mechanism to load the
                        // response handlers
                        //that are scheduled to run in the original response
                        // path.
                        //Need to re-send messsages if we didn't get a
                        // response.
                        //RMMessageContext a field to store the long
                        // lastProcessedTime
                        //Another field to hold retransmission count.

                        System.out.println("SENDING RESPONSE MESSAGE .....\n");

                        ServerQueue sq = ServerQueue.getInstance();
                        sq.displayPriorityQueue();
                        sq.displayOutgoingMap();
                        sq.displayIncomingMap();

                        SOAPEnvelope responseEnvelope = null;

                        if (rmMessageContext.getReTransmissionCount() <= Constants.MAXIMUM_RETRANSMISSION_COUNT) {
                            if ((System.currentTimeMillis() - rmMessageContext
                                    .getLastPrecessedTime()) > Constants.RETRANSMISSION_INTERVAL) {

                                //TODO
                                //We should do this only once and then need to
                                //Save the respones message.
                                //if (rmMessageContext.getReTransmissionCount()
                                // == 0) {
                                //Need to create the response envelope.
                                responseEnvelope = EnvelopeCreator
                                        .createServiceResponseEnvelope(rmMessageContext);
                                rmMessageContext.getMsgContext()
                                        .setRequestMessage(
                                                new Message(responseEnvelope));
                                System.out.println(responseEnvelope);
                                //}

                                System.out.println(rmMessageContext
                                        .getAddressingHeaders().getReplyTo()
                                        .getAddress().toString());

                                try {
                                    Service service = new Service();
                                    Call call = (Call) service.createCall();
                                    //call.setTargetEndpointAddress(rmMessageContext.getOutGoingAddress());
                                    call
                                            .setTargetEndpointAddress(rmMessageContext
                                                    .getAddressingHeaders()
                                                    .getReplyTo().getAddress()
                                                    .toString());

                                    //NOTE: WE USE THE REQUEST MESSAGE TO SEND
                                    // THE RESPONSE.

                                    call.setRequestMessage(rmMessageContext
                                            .getMsgContext()
                                            .getRequestMessage());
                                    //System.out.println(rmMessageContext.getMsgContext().getResponseMessage().getSOAPPartAsString());
                                    try {
                                        rmMessageContext
                                                .setLastPrecessedTime(System
                                                        .currentTimeMillis());
                                        rmMessageContext
                                                .setReTransmissionCount(rmMessageContext
                                                        .getReTransmissionCount() + 1);
                                        System.out
                                                .println("INVOKING THE RESPONSE MESSAGE 88888888888888888888888");
                                        //We are not expecting the ack over the
                                        // same HTTP connection.
                                        call.invoke();
                                        //System.out.println(call.getResponseMessage().getSOAPPartAsString());
                                    } catch (AxisFault e) {
                                        e.printStackTrace();
                                        break;
                                    }

                                } catch (ServiceException e1) {
                                    System.out
                                            .println("(!)(!)(!)Cannot send the Response message.....");
                                    e1.printStackTrace();
                                    break;
                                }
                            }
                            break;
                        }

                    }
                        break;
                    }

                }

            } while (hasMessages);

            long timeGap = System.currentTimeMillis() - startTime;
            if ((timeGap - Constants.SENDER_SLEEP_TIME) <= 0) {
                try {

                    System.out
                            .println("Sender  THREAD IS SLEEPING    -----------XXX----------\n");

                    Thread.sleep(Constants.SENDER_SLEEP_TIME - timeGap);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }
        }
    }

    private void sendCreateSequenceRequest(RMMessageContext rmMessageContext)throws RMException{
        try {
            System.out.println("CREATE SEQ REQUESET");
            //Send the message.
            //may get the reply back to here.
            Service service = new Service();
            Call call = (Call) service.createCall();
            System.out.println(rmMessageContext
                    .getOutGoingAddress());
            call.setTargetEndpointAddress(rmMessageContext
                    .getOutGoingAddress());
            if (rmMessageContext.getMsgContext()
                    .getRequestMessage() == null)
                System.out.println("NULL REQUEST MESSAGE");

            call.setRequestMessage(rmMessageContext
                    .getMsgContext().getRequestMessage());
            try {
                //Send the createSequnceRequest.
                call.invoke();
            } catch (AxisFault e) {
                e.printStackTrace();
                throw new RMException("ERROR : IN SENDING THE CREATE SEQ MESSAGE");
            }
            
            //Check whther we have a response. If so then use to set the response.
            if (call.getResponseMessage() != null) {
                rmMessageContext.getMsgContext()
                        .setResponseMessage(
                                call.getResponseMessage());
                IRMMessageProcessor messagePrcessor = RMMessageProcessorIdentifier
                        .getMessageProcessor(rmMessageContext,
                                storageManager);
                if (messagePrcessor instanceof FaultProcessor) {
                    //process the fault.
                    //For now just ignore.
                    System.out
                            .println("Fault for the CreateSequenceRequest");
                    //For testing only.
                    //storageManager.setApprovedOutSequence(
                    //        "abcdefghijk", "1233abcdefghijk");
                } else if (messagePrcessor instanceof CreateSequenceResponseProcessor) {
                    try {
                        messagePrcessor
                                .processMessage(rmMessageContext);
                    } catch (RMException rmEx) {
                        throw new RMException("ERROR: IN PROCESSING THE ASYNC CREATE SEQUENCE RESPONSE");
                    }
                }

            }

        } catch (ServiceException e1) {
           throw new RMException("ERROR: SERVICE EXCEPTION WHEN SENDING CREATE SEQUENCE REQUEST");
            
        }
    }
    
    private void sendCreateSequenceResponse(RMMessageContext rmMessageContext)throws RMException{
        try {
            System.out.println("CREATE SEQ RESPONSE");
            //System.out
            //         .println("******** Sending the
            // message**************");
            System.out
                    .println(rmMessageContext.getMsgContext()
                            .getResponseMessage()
                            .getSOAPPartAsString());
            //System.out
            //        .println("******** Sending the
            // message**************");
            Service service = new Service();
            Call call = (Call) service.createCall();
            System.out.println(rmMessageContext
                    .getOutGoingAddress());
            call.setTargetEndpointAddress(rmMessageContext
                    .getOutGoingAddress());
            if (rmMessageContext.getMsgContext()
                    .getResponseMessage() == null)
                System.out.println("NULL RESPONSE MESSAGE");

            call.setRequestMessage(rmMessageContext
                    .getMsgContext().getResponseMessage());
            call.invoke();
        } catch (ServiceException e1) {
            throw new RMException("ERROR: SERVICE EXCEPTION WHEN SENDING CREATE SEQUENCE RESPONSE");
        } catch (AxisFault e) {
            e.printStackTrace();
        }
    }
    
    private void sendAcknowldgement(RMMessageContext rmMessageContext)throws RMException{
        try {
            Service service = new Service();
            Call call = (Call) service.createCall();
            System.out.println(rmMessageContext
                    .getOutGoingAddress());
            call.setTargetEndpointAddress(rmMessageContext
                    .getOutGoingAddress());
            call.setRequestMessage(rmMessageContext
                    .getMsgContext().getResponseMessage());
            System.out
                    .println(rmMessageContext.getMsgContext()
                            .getResponseMessage()
                            .getSOAPPartAsString());
            call.invoke();
        } catch (ServiceException e1) {
            System.out
                    .println("(!)(!)(!)Cannot send the Ack message.");
            throw new RMException("ERROR: SENDING THE ACKNOWLEDGEMTN MESSAGE");
            
        } catch (AxisFault e) {
            e.printStackTrace();
            
        }
    }
    
}