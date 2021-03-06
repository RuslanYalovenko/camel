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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link org.apache.camel.processor.ErrorHandler} used as a safe fallback when
 * processing by other error handlers such as the {@link org.apache.camel.model.OnExceptionDefinition}.
 *
 * @version
 */
public class FatalFallbackErrorHandler extends DelegateAsyncProcessor implements ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FatalFallbackErrorHandler.class);

    public FatalFallbackErrorHandler(Processor processor) {
        super(processor);
    }

    @Override
    protected boolean processNext(final Exchange exchange, final AsyncCallback callback) {
        // support the asynchronous routing engine
        boolean sync = super.processNext(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (exchange.getException() != null) {
                    // an exception occurred during processing onException

                    // log detailed error message with as much detail as possible
                    Throwable previous = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                    String msg = "Exception occurred while trying to handle previously thrown exception on exchangeId: "
                            + exchange.getExchangeId() + " using: [" + processor + "].";
                    if (previous != null) {
                        msg += " The previous and the new exception will be logged in the following.";
                        LOG.error(msg);
                        LOG.error("\\--> Previous exception on exchangeId: " + exchange.getExchangeId() , previous);
                        LOG.error("\\--> New exception on exchangeId: " + exchange.getExchangeId(), exchange.getException());
                    } else {
                        LOG.error(msg);
                        LOG.error("\\--> New exception on exchangeId: " + exchange.getExchangeId(), exchange.getException());
                    }

                    // we can propagated that exception to the caught property on the exchange
                    // which will shadow any previously caught exception and cause this new exception
                    // to be visible in the error handler
                    exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exchange.getException());

                    // mark this exchange as already been error handler handled (just by having this property)
                    // the false value mean the caught exception will be kept on the exchange, causing the
                    // exception to be propagated back to the caller, and to break out routing
                    exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, false);
                }
                callback.done(doneSync);
            }
        });

        return sync;
    }

    @Override
    public String toString() {
        return "FatalFallbackErrorHandler[" + processor + "]";
    }
}
