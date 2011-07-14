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

package net.lshift.diffa.kernel.actors

import org.junit.{Test, After, Before}
import org.easymock.EasyMock._
import org.junit.Assert._
import net.lshift.diffa.kernel.differencing._
import org.joda.time.DateTime
import net.lshift.diffa.kernel.events.{UpstreamPairChangeEvent, VersionID}
import net.lshift.diffa.kernel.config.{GroupContainer, ConfigStore, Endpoint}
import net.lshift.diffa.kernel.participants._
import org.easymock.{EasyMock, IAnswer}
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.classic.spi.ILoggingEvent
import java.lang.RuntimeException
import akka.actor._
import concurrent.{SyncVar, TIMEOUT, MailBox}
import net.lshift.diffa.kernel.diag.{DiagnosticLevel, DiagnosticsManager}
import net.lshift.diffa.kernel.util.{EasyMockScalaUtils, AlertCodes}

class PairActorTest {

  val pairKey = "some-pairing"
  val policyName = ""
  val upstream = Endpoint(name = "up", scanUrl = "up", contentType = "application/json")
  val downstream = Endpoint(name = "down", scanUrl = "down", contentType = "application/json")

  val pair = new net.lshift.diffa.kernel.config.Pair()
  pair.key = pairKey
  pair.versionPolicyName = policyName
  pair.upstream = upstream
  pair.downstream = downstream

  val us = createStrictMock("upstreamParticipant", classOf[UpstreamParticipant])
  val ds = createStrictMock("downstreamParticipant", classOf[DownstreamParticipant])
  val diagnostics = createStrictMock("diagnosticsManager", classOf[DiagnosticsManager])

  val participantFactory = org.easymock.classextension.EasyMock.createStrictMock("participantFactory", classOf[ParticipantFactory])
  expect(participantFactory.createUpstreamParticipant(upstream)).andReturn(us)
  expect(participantFactory.createDownstreamParticipant(downstream)).andReturn(ds)
  org.easymock.classextension.EasyMock.replay(participantFactory)

  val versionPolicyManager = org.easymock.classextension.EasyMock.createStrictMock("versionPolicyManager", classOf[VersionPolicyManager])

  val versionPolicy = createMock("versionPolicy", classOf[VersionPolicy])
  checkOrder(versionPolicy, false)

  expect(versionPolicyManager.lookupPolicy(policyName)).andReturn(Some(versionPolicy))
  org.easymock.classextension.EasyMock.replay(versionPolicyManager)

  val configStore = createStrictMock("configStore", classOf[ConfigStore])
  expect(configStore.listGroups).andReturn(Array[GroupContainer]())
  replay(configStore)

  val writer = createMock("writer", classOf[ExtendedVersionCorrelationWriter])

  val store = createMock("versionCorrelationStore", classOf[VersionCorrelationStore])
  expect(store.openWriter()).andReturn(writer).anyTimes
  replay(store)

  val stores = createStrictMock("versionCorrelationStoreFactory", classOf[VersionCorrelationStoreFactory])
  expect(stores.apply(pairKey)).andReturn(store)
  replay(stores)

  val escalationListener = createMock(classOf[DifferencingListener])

  val supervisor = new PairActorSupervisor(versionPolicyManager, configStore, escalationListener, participantFactory, stores, diagnostics, 50, 100)
  supervisor.onAgentAssemblyCompleted
  supervisor.onAgentConfigurationActivated

  verify(configStore)

  val diffListener = createStrictMock("differencingListener", classOf[DifferencingListener])
  val syncListener = createStrictMock("syncListener", classOf[PairSyncListener])

  @After
  def stop = supervisor.stopActor(pairKey)

  // Check for spurious actor events
  val spuriousEventAppender = new SpuriousEventAppender

  @Before
  def attachAppenderToContext = {
    val ctx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    spuriousEventAppender.setContext(ctx)
    spuriousEventAppender.start()
    val log = LoggerFactory.getLogger(classOf[PairActor]).asInstanceOf[ch.qos.logback.classic.Logger]
    log.addAppender(spuriousEventAppender)
  }

  @After
  def checkForSpuriousEvents = {
    if (spuriousEventAppender.hasSpuriousEvent) {
      fail("Should not have receied a spurious message: %s".format(spuriousEventAppender.lastEvent.getMessage))
    }
  }

  def expectUpstreamScan() = {
    expect(versionPolicy.scanUpstream(EasyMock.eq(pairKey), EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
                                      EasyMock.eq(us), EasyMock.isA(classOf[DifferencingListener]),
                                      EasyMock.isA(classOf[FeedbackHandle])))
  }
  def expectDownstreamScan() = {
    expect(versionPolicy.scanDownstream(EasyMock.eq(pairKey), EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
                                        EasyMock.eq(us), EasyMock.eq(ds), EasyMock.isA(classOf[DifferencingListener]),
                                        EasyMock.isA(classOf[FeedbackHandle])))
  }

  def expectScans() = {
    expectUpstreamScan()
    expectDownstreamScan()
  }

  def expectDifferencesReplay() = {
    expect(versionPolicy.replayUnmatchedDifferences(pairKey, diffListener))
    expect(versionPolicy.replayUnmatchedDifferences(pairKey, escalationListener))
  }

  def expectWriterRollback() {
    expect(writer.flush()).atLeastOnce
    expect(writer.rollback())
    replay(writer)
  }

  @Test
  def runDifference = {
    val monitor = new Object
    expect(versionPolicy.replayUnmatchedDifferences(pairKey, diffListener)).andAnswer(new IAnswer[Unit] {
      def answer = { monitor.synchronized { monitor.notifyAll } }
    })

    replay(versionPolicy)

    supervisor.startActor(pair)
    supervisor.difference(pairKey, diffListener)

    monitor.synchronized {
      monitor.wait(1000)
    }

    verify(versionPolicy)
  }

  @Test
  def runScan = {
    val monitor = new Object

    expect(writer.flush()).atLeastOnce
    replay(writer)
    syncListener.pairSyncStateChanged(pairKey, PairScanState.SYNCHRONIZING); expectLastCall

    expectScans

    expectDifferencesReplay()

    syncListener.pairSyncStateChanged(pairKey, PairScanState.UP_TO_DATE); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = { monitor.synchronized { monitor.notifyAll } }
    })
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pairKey, "Scan completed"); expectLastCall
    replay(versionPolicy, syncListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pairKey, diffListener, syncListener)
    monitor.synchronized {
      monitor.wait(1000)
    }
    verify(versionPolicy, syncListener, diagnostics)
  }

  @Test
  def backlogShouldBeProcessedAfterScan = {
    val flushMonitor = new Object
    val eventMonitor = new Object

    val event = buildUpstreamEvent()

    syncListener.pairSyncStateChanged(pairKey, PairScanState.SYNCHRONIZING); expectLastCall
    syncListener.pairSyncStateChanged(pairKey, PairScanState.UP_TO_DATE); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = { flushMonitor.synchronized { flushMonitor.notifyAll } }
    })
    replay(syncListener)

    expect(versionPolicy.onChange(writer, event))
    .andAnswer(new IAnswer[Unit] {
      def answer = {
        eventMonitor.synchronized {
          eventMonitor.notifyAll
        }
      }
    })

    expect(versionPolicy.scanUpstream(EasyMock.eq(pairKey),
           EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
           EasyMock.eq(us),
           EasyMock.isA(classOf[DifferencingListener]),
           EasyMock.isA(classOf[FeedbackHandle]))
    ).andAnswer(new IAnswer[Unit] {
      def answer = {
        // Queue up a change event and block the actor in the scanning state for a 1 sec
        supervisor.propagateChangeEvent(event)
        Thread.sleep(1000)
      }
    })
    expect(versionPolicy.scanDownstream(EasyMock.eq(pairKey),
           EasyMock.isA(classOf[LimitedVersionCorrelationWriter]),
           EasyMock.eq(us), EasyMock.eq(ds),
           EasyMock.isA(classOf[DifferencingListener]),
           EasyMock.isA(classOf[FeedbackHandle])))

    expectDifferencesReplay()

    replay(versionPolicy)

    supervisor.startActor(pair)
    supervisor.scanPair(pairKey, diffListener, syncListener)

    flushMonitor.synchronized {
      flushMonitor.wait(2000)
    }
    eventMonitor.synchronized {
      eventMonitor.wait(2000)
    }

    verify(versionPolicy)
    verify(syncListener)
  }

  @Test
  def shouldHandleCancellationWhilstNotScanning = {
    supervisor.startActor(pair)
    assertTrue(supervisor.cancelScans(pairKey))
  }


  @Test
  def shouldHandleCancellationWhilstScanning = {

    val cancelMonitor = new Object
    val responseMonitor = new Object

    val event = buildUpstreamEvent()

    val timeToWait = 2000L

    syncListener.pairSyncStateChanged(pairKey, PairScanState.SYNCHRONIZING); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = {
        Actor.spawn {
          // Request a cancellation in a background thread so that the pair actor can be scheduled
          // in to process the cancellation. Notifying the main test thread that the request
          // returned true is the same thing as assertTrue(supervisor.cancelScans(pairKey))
          // except that the assertion is effectively on the main test thread.
          if (supervisor.cancelScans(pairKey)) {
            responseMonitor.synchronized{ responseMonitor.notifyAll() }
          }
        }
      }
    })

    syncListener.pairSyncStateChanged(pairKey, PairScanState.CANCELLED); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer = cancelMonitor.synchronized{ cancelMonitor.notifyAll() }
    })
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pairKey, "Scan cancelled"); expectLastCall

    expectScans.andAnswer(new IAnswer[Unit] {
      def answer = {
        // Push a change event through in the cancellation state
        // The actor should drop this message and hence no invocation
        // should be made against the versionPolicy mock object
        supervisor.propagateChangeEvent(event)

        // Put the sub actor into a sufficiently long pause so that the cancellation request
        // has enough time to get processed by the parent actor, have it trigger the
        // the scan state listener and send a response back the thread that requested the
        // cancellation
        Thread.sleep(timeToWait)
      }
    })

    replay(versionPolicy, syncListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pairKey, diffListener, syncListener)

    responseMonitor.synchronized {
      responseMonitor.wait(timeToWait * 2)
    }

    cancelMonitor.synchronized {
      cancelMonitor.wait(timeToWait * 2)
    }

    verify(versionPolicy, syncListener, diagnostics)
  }

  @Test
  def shouldReportScanFailure = {
    val monitor = new Object

    expectFailingScan(
      downstreamHandler = EasyMockScalaUtils.emptyAnswer,
      failStateHandler = new IAnswer[Unit] {
          def answer { monitor.synchronized { monitor.notifyAll } }
        })
    replay(versionPolicy, syncListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pairKey, diffListener, syncListener)
    monitor.synchronized {
      monitor.wait(1000)
    }
    verify(versionPolicy, syncListener)
    verify(syncListener)
  }

  @Test
  def shouldCancelFeedbackHandleWhenAParticipantFails = {
    val wasMarkedAsCancelled = new SyncVar[Boolean]

    expectFailingScan(downstreamHandler = new IAnswer[Unit] {
      def answer() {
        val feedbackHandle = EasyMock.getCurrentArguments()(5).asInstanceOf[FeedbackHandle]
        awaitFeedbackHandleCancellation(feedbackHandle)
        wasMarkedAsCancelled.set(feedbackHandle.isCancelled)
      }
    })
    replay(versionPolicy, syncListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pairKey, diffListener, syncListener)
    assertTrue(wasMarkedAsCancelled.get(4000).getOrElse(throw new Exception("Feedback handle check never reached in participant stub")))
    verify(versionPolicy, syncListener)
    verify(syncListener)
  }

  @Test
  def shouldGenerateExceptionInVersionCorrelationWriterProxyWhenAParticipantFails = {
    val proxyDidGenerateException = new SyncVar[Boolean]

    expectFailingScan(downstreamHandler = new IAnswer[Unit] {
      def answer() {
        val feedbackHandle = EasyMock.getCurrentArguments()(5).asInstanceOf[FeedbackHandle]
        awaitFeedbackHandleCancellation(feedbackHandle)

        val writer = EasyMock.getCurrentArguments()(1).asInstanceOf[LimitedVersionCorrelationWriter]
        try {
          writer.clearDownstreamVersion(VersionID("p1", "abc"))
          proxyDidGenerateException.set(false)
        } catch {
          case c:ScanCancelledException => proxyDidGenerateException.set(true)
        }
      }
    })
    replay(versionPolicy, syncListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pairKey, diffListener, syncListener)
    
    assertTrue(proxyDidGenerateException.get(4000).getOrElse(throw new Exception("Exception validation never reached in participant stub")))
    verify(versionPolicy, syncListener)
    verify(syncListener)
  }

  @Test
  def shouldGenerateExceptionInVersionCorrelationWriterProxyWhenAParticipantFailsAndANewScanHasStarted = {
    // NOTE: We have a couple of different actions blocking on different threads. EasyMock by default will make
    //       mocks threadsafe, which results in a deadlock. It isn't clear whether we'll have problems with disabling
    //       the threadsafety given we are calling the mock from multiple threads, but without completely skipping the
    //       use of EasyMock for this test, there aren't many other options.
    makeThreadSafe(versionPolicy, false)

    val proxyDidGenerateException = new SyncVar[Boolean]
    val secondScanIsRunning = new SyncVar[Boolean]
    val completionMonitor = new Object

    expectFailingScan(
      downstreamHandler = new IAnswer[Unit] {
          def answer() {
            if (secondScanIsRunning.get(10000).isDefined) {
              val writer = EasyMock.getCurrentArguments()(1).asInstanceOf[LimitedVersionCorrelationWriter]
              try {
                writer.clearDownstreamVersion(VersionID("p1", "abc"))
                proxyDidGenerateException.set(false)
              } catch {
                case c:ScanCancelledException => proxyDidGenerateException.set(true)
              }
            } else {
              println("Expired whilst waiting for second scan to start")
            }
          }
        },
      failStateHandler = new IAnswer[Unit] {
          def answer {
            supervisor.scanPair(pairKey, diffListener, syncListener)      // Run a second scan when the first one fails
          }
        })
    
    syncListener.pairSyncStateChanged(pairKey, PairScanState.SYNCHRONIZING); expectLastCall()
    expectUpstreamScan().once()  // Succeed on second
    expectDownstreamScan().andAnswer(new IAnswer[Unit] {
      def answer() {
        // Notify that the second scan has started
        secondScanIsRunning.set(true)

        // Block the second scan until the first has reached a conclusion
        proxyDidGenerateException.get(4000)
      }
    }).once()
    expectDifferencesReplay()
    diagnostics.logPairEvent(DiagnosticLevel.INFO, pairKey, "Scan completed"); expectLastCall
    syncListener.pairSyncStateChanged(pairKey, PairScanState.UP_TO_DATE); expectLastCall[Unit].andAnswer(new IAnswer[Unit] {
      def answer {
        completionMonitor.synchronized { completionMonitor.notifyAll() }
      }
    }).once()

    replay(versionPolicy, syncListener, diagnostics)

    supervisor.startActor(pair)
    supervisor.scanPair(pairKey, diffListener, syncListener)

    assertTrue(proxyDidGenerateException.get(100000).getOrElse(throw new Exception("Exception validation never reached in participant stub")))
    completionMonitor.synchronized { completionMonitor.wait(1000) }   // Wait for the scan to complete too

    verify(versionPolicy, syncListener)
    verify(syncListener)
  }

  @Test
  def propagateChange = {
    val id = VersionID(pairKey, "foo")
    val lastUpdate = new DateTime()
    val vsn = "foobar"
    val event = UpstreamPairChangeEvent(id, Seq(), lastUpdate, vsn)
    val monitor = new Object

    expect(writer.flush()).atLeastOnce
    replay(writer)
    expect(versionPolicy.onChange(writer, event)).andAnswer(new IAnswer[Unit] {
      def answer = {
        monitor.synchronized {
          monitor.notifyAll
        }
      }
    })
    replay(versionPolicy)

    supervisor.startActor(pair)
    supervisor.propagateChangeEvent(event)

    // propagateChangeEvent is an aysnc call, so yield the test thread to allow the actor to invoke the policy
    monitor.synchronized {
      monitor.wait(1000)
    }

    verify(versionPolicy)
  }

  @Test
  def scheduledFlush {
    val mailbox = new MailBox
    
    expect(writer.flush()).andStubAnswer(new IAnswer[Unit] {
      def answer = {
        mailbox.send(new Object)
        null
      }
    })
    replay(writer)

    supervisor.startActor(pair)
    mailbox.receiveWithin(1000) { case TIMEOUT => fail("Flush not called"); case _ => () }
    mailbox.receiveWithin(1000) { case TIMEOUT => fail("Flush not called"); case _ => () }
  }

  def expectFailingScan(downstreamHandler:IAnswer[Unit], failStateHandler:IAnswer[Unit] = EasyMockScalaUtils.emptyAnswer) {
    expectWriterRollback()

    syncListener.pairSyncStateChanged(pairKey, PairScanState.SYNCHRONIZING); expectLastCall.once

    expectUpstreamScan().andThrow(new RuntimeException("Deliberate runtime exception, this should be handled")).once()
    expectDownstreamScan().andAnswer(downstreamHandler).once()

    syncListener.pairSyncStateChanged(pairKey, PairScanState.FAILED); expectLastCall[Unit].andAnswer(failStateHandler).once
    diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairKey, "Upstream scan failed: Deliberate runtime exception, this should be handled"); expectLastCall.once
    diagnostics.logPairEvent(DiagnosticLevel.ERROR, pairKey, "Scan failed"); expectLastCall.once
  }

  def buildUpstreamEvent() = {
    val id = VersionID(pairKey, "foo")
    val lastUpdate = new DateTime
    val vsn = "foobar"
    UpstreamPairChangeEvent(id, Seq(), lastUpdate, vsn)
  }

  def awaitFeedbackHandleCancellation(feedbackHandle:FeedbackHandle) {
    val endTime = System.currentTimeMillis() + 2000
    while (!feedbackHandle.isCancelled && endTime > System.currentTimeMillis()) {
      Thread.sleep(100)
    }
  }
}

/**
 * Simple logback appender that detects whether the PairActor has received a spurious message
 */
class SpuriousEventAppender extends AppenderBase[ILoggingEvent]{

  var hasSpuriousEvent = false
  var lastEvent:ILoggingEvent = null

  def append(event:ILoggingEvent) = {
    lastEvent = event
    if (event.getFormattedMessage.contains(AlertCodes.SPURIOUS_ACTOR_MESSAGE + ":")) {
      hasSpuriousEvent = true
    }
  }

}