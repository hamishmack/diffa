/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.messaging.amqp

import org.apache.commons.io.IOUtils
import org.junit.Assert._
import org.junit.Assume.assumeTrue
import org.junit.Test
import net.lshift.diffa.kernel.protocol.{TransportResponse, TransportRequest, ProtocolHandler}
import com.rabbitmq.messagepatterns.unicast.ReceivedMessage

/**
 * Test cases for fire-and-forget AMQP messaging.
 */
class AmqpProducerConsumerTests {

  assumeTrue(AmqpConnectionChecker.isConnectionAvailable)

  @Test(timeout = 1000)
  def fireAndForget() {
    val queueName = "testQueue"
    val holder = new ConnectorHolder()

    val consumer = new AmqpConsumer(holder.connector,
                                    queueName,
                                    new EndpointMapper { def apply(msg: ReceivedMessage) = "" },
                                    new ProtocolHandler {
      val contentType = "text/plain"

      def handleRequest(req: TransportRequest, res: TransportResponse) = {
        assertEquals("expected payload", IOUtils.toString(req.is))
        true
      }
    })
    
    val producer = new AmqpProducer(holder.connector, queueName)
    producer.send("expected payload")
  }
}