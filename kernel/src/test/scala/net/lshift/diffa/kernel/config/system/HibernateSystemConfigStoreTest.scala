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

package net.lshift.diffa.kernel.config.system

import org.junit.Assert._
import collection.JavaConversions._
import net.lshift.diffa.kernel.util.SessionHelper._
import org.joda.time.DateTime
import net.lshift.diffa.kernel.frontend.{PairDef, EndpointDef}
import org.junit.{Before, Test}
import net.lshift.diffa.kernel.config.{User, Domain, HibernateDomainConfigStoreTest, DomainConfigStore, Pair => DiffaPair, RangeCategoryDescriptor, Endpoint}
import collection.mutable.HashSet

class HibernateSystemConfigStoreTest {

  private val domainConfigStore: DomainConfigStore = HibernateDomainConfigStoreTest.domainConfigStore
  private val sf = HibernateDomainConfigStoreTest.domainConfigStore.sessionFactory
  private val systemConfigStore:SystemConfigStore = new HibernateSystemConfigStore(domainConfigStore, sf)

  val domainName = "domain"
  val domain = Domain(name=domainName)

  val versionPolicyName1 = "TEST_VPNAME"
  val matchingTimeout = 120
  val versionPolicyName2 = "TEST_VPNAME_ALT"
  val pairKey = "TEST_PAIR"

  val bound = new DateTime().toString()
  val categories = Map("cat" ->  new RangeCategoryDescriptor("datetime", bound, bound))

  val upstream1 = new EndpointDef(name = "TEST_UPSTREAM", scanUrl = "testScanUrl1",
                               inboundUrl = "http://foo.com",
                               contentType = "application/json", categories = categories)
  val downstream1 = new EndpointDef(name = "TEST_DOWNSTREAM", scanUrl = "testScanUrl3",
                                 inboundUrl = "http://bar.com",
                                 contentType = "application/json", categories = categories)

  val pairDef = new PairDef(pairKey, versionPolicyName1, matchingTimeout, upstream1.name,
    downstream1.name)

  val TEST_USER = User("foo", null, "foo@bar.com")

  @Before
  def setup = {
    systemConfigStore.deleteDomain(domainName)
    systemConfigStore.createOrUpdateDomain(domain)
  }

  @Test
  def shouldBeAbleToSetSystemProperty = {
    systemConfigStore.createOrUpdateDomain(Domain.DEFAULT_DOMAIN)
    systemConfigStore.setSystemConfigOption("foo", "bar")
    assertEquals("bar", systemConfigStore.maybeSystemConfigOption("foo").get)
  }

  @Test
  def testQueryingForAssociatedPairsReturnsNothingForUnusedEndpoint {
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)
    assertEquals(0, systemConfigStore.getPairsForInboundEndpointURL(upstream1.name).length)
  }

  @Test
  def testQueryingForAssociatedPairsReturnsPairUsingEndpointAsUpstream {
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)
    domainConfigStore.createOrUpdateEndpoint(domainName, downstream1)
    domainConfigStore.createOrUpdatePair(domainName, PairDef(pairKey, versionPolicyName2, DiffaPair.NO_MATCHING,
                                               upstream1.name, downstream1.name))

    val res = systemConfigStore.getPairsForInboundEndpointURL(upstream1.inboundUrl)
    assertEquals(1, res.length)
    assertEquals(pairKey, res(0).key)
  }

  @Test
  def testQueryingForAssociatedPairsReturnsPairUsingEndpointAsDownstream {
    domainConfigStore.createOrUpdateEndpoint(domainName, upstream1)
    domainConfigStore.createOrUpdateEndpoint(domainName, downstream1)
    domainConfigStore.createOrUpdatePair(domainName, PairDef(pairKey, versionPolicyName2, DiffaPair.NO_MATCHING,
                                               upstream1.name, downstream1.name))

    val res = systemConfigStore.getPairsForInboundEndpointURL(downstream1.inboundUrl)
    assertEquals(1, res.length)
    assertEquals(pairKey, res(0).key)
  }

  // TODO It is still unclear as to whether user management should be in the system config interface
  @Test
  def testUserCRUD = {

    // TODO
    // During re-runs of tests, some users may be left behind
    // Because of the m:n relationship between users and domains,
    // we need to figure out how we are going to construct the API for this
    // so for now, nuke the table manually.

    sf.withSession( s => {
      s.createCriteria(classOf[User]).list.foreach(u => {
        val user = u.asInstanceOf[User]
        user.domains.clear
        s.delete(u)
      })
    })

    systemConfigStore.createOrUpdateUser(TEST_USER)
    val result = systemConfigStore.listUsers
    assertEquals(1, result.length)
    // Hibernate doesn't seem to able to hydrate the many-to-many eagerly,
    // so let's just verify that the user object is fine for now
    assertEquals(TEST_USER.name, result(0).name)
    val updated = User(TEST_USER.name, HashSet(Domain(name = "domain")), "somethingelse@bar.com")
    systemConfigStore.createOrUpdateUser(updated)
    val user = systemConfigStore.getUser(TEST_USER.name)
    // See note above about lazy fetching
    assertEquals(updated.name, user.name)
    systemConfigStore.deleteUser(TEST_USER.name)
    val users = systemConfigStore.listUsers
    assertEquals(0, users.length)
  }
}