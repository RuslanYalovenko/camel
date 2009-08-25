/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class ManagedDelayerTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DefaultManagementNamingStrategy naming = (DefaultManagementNamingStrategy) context.getManagementStrategy().getManagementNamingStrategy();
        naming.setHostName("localhost");
        naming.setDomainName("org.apache.camel");
        return context;
    }

    @SuppressWarnings("unchecked")
    public void testManageDelay() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        // get the object name for the delayer
        ObjectName delayerName = ObjectName.getInstance("org.apache.camel:context=localhost/camel-1,type=processors,name=\"mydelayer\"");

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=localhost/camel-1,type=routes,name=\"route1\"");
        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(1, completed.longValue());

        Long last = (Long) mbeanServer.getAttribute(routeName, "LastProcessingTime");
        Long total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue("Should take around 1 sec: was " + last, last > 900);
        assertTrue("Should take around 1 sec: was " + total, total > 900);

        // change the delay time using JMX
        mbeanServer.invoke(delayerName, "constantDelay", new Object[]{2000}, new String[]{"java.lang.Integer"});

        // send in another message
        template.sendBody("direct:start", "Bye World");

        Expression delay = (Expression) mbeanServer.getAttribute(delayerName, "Delay");
        assertNotNull(delay);

        completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(2, completed.longValue());
        last = (Long) mbeanServer.getAttribute(routeName, "LastProcessingTime");
        total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue("Should take around 2 sec: was " + last, last > 1900);
        assertTrue("Should be around 3 sec now: was " + total, total > 2900);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("log:foo")
                    .delay(1000).id("mydelayer")
                    .to("mock:result");
            }
        };
    }

}
