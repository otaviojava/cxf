/**
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
package org.apache.cxf.transport.jms;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.testutil.common.EmbeddedJMSBrokerLauncher;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.wsdl11.WSDLServiceFactory;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;

public abstract class AbstractJMSTester extends Assert {
    public static final String JMS_PORT = EmbeddedJMSBrokerLauncher.PORT;
    
    protected static final String MESSAGE_CONTENT = "HelloWorld";
    
    private static JMSBrokerSetup broker;

    protected Bus bus;
    protected EndpointInfo endpointInfo;
    protected EndpointReferenceType target;
    protected MessageObserver observer;
    protected Message inMessage;
    protected String wsdlURL;

    public static void startBroker(JMSBrokerSetup b) throws Exception {
        assertNotNull(b);
        broker = b;
        broker.start();
    }

    @AfterClass
    public static void stopBroker() throws Exception {
        broker.stop();
        broker = null;
    }

    @Before
    public void setUp() {
        BusFactory bf = BusFactory.newInstance();
        bus = bf.createBus();
        BusFactory.setDefaultBus(bus);
    }

    @After
    public void tearDown() {
        bus.shutdown(true);
        if (System.getProperty("cxf.config.file") != null) {
            System.clearProperty("cxf.config.file");
        }
        wsdlURL = null;
    }

    protected void setupServiceInfo(String ns, String wsdl, String serviceName, String portName) {
        URL wsdlUrl = getClass().getResource(wsdl);
        wsdlURL = wsdlUrl.toString();
        assertNotNull(wsdlUrl);
        EmbeddedJMSBrokerLauncher.updateWsdlExtensors(bus, wsdlURL);
        WSDLServiceFactory factory = new WSDLServiceFactory(bus, wsdlURL, new QName(ns, serviceName));

        Service service = factory.create();
        endpointInfo = service.getEndpointInfo(new QName(ns, portName));

    }

    protected void sendoutMessage(Conduit conduit, Message message, Boolean isOneWay) throws IOException {

        Exchange exchange = new ExchangeImpl();
        exchange.setOneWay(isOneWay);
        exchange.setSynchronous(true);
        message.setExchange(exchange);
        exchange.setOutMessage(message);
        try {
            conduit.prepare(message);
        } catch (IOException ex) {
            assertFalse("JMSConduit can't perpare to send out message", false);
            ex.printStackTrace();
        }
        OutputStream os = message.getContent(OutputStream.class);
        Writer writer = message.getContent(Writer.class);
        assertTrue("The OutputStream and Writer should not both be null ", os != null || writer != null);
        if (os != null) {
            os.write(MESSAGE_CONTENT.getBytes()); // TODO encoding
            os.close();
        } else {
            writer.write(MESSAGE_CONTENT);
            writer.close();
        }
    }

    protected void adjustEndpointInfoURL() {
        if (endpointInfo != null) {
            AddressType at = endpointInfo.getExtensor(AddressType.class);
            if (at != null) {
                for (JMSNamingPropertyType jnt : at.getJMSNamingProperty()) {
                    if (jnt.getName().equals("java.naming.provider.url")) {
                        String v = jnt.getValue();
                        v = v.replace("61500", JMS_PORT);
                        v = v.replace("61616", JMS_PORT);
                        jnt.setValue(v);
                    }
                }
            }
        }
    }
    
    protected JMSConduit setupJMSConduit(boolean send, boolean decoupled) throws IOException {
        if (decoupled) {
            // setup the reference type
        } else {
            target = EasyMock.createMock(EndpointReferenceType.class);
        }
        
        adjustEndpointInfoURL();

        JMSConfiguration jmsConfig = new JMSOldConfigHolder()
            .createJMSConfigurationFromEndpointInfo(bus, endpointInfo, null, true);
        JMSConduit jmsConduit = new JMSConduit(endpointInfo, target, jmsConfig, bus);
        if (send) {
            // setMessageObserver
            observer = new MessageObserver() {
                public void onMessage(Message m) {
                    inMessage = m;
                }
            };
            jmsConduit.setMessageObserver(observer);
        }

        return jmsConduit;
    }

}
