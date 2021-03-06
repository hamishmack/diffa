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

package net.lshift.diffa.kernel.differencing

import net.lshift.diffa.kernel.events.VersionID
import java.lang.String
import org.joda.time.DateTime

/**
 * Differencing listener that just ignores all reports.
 */
class NullDifferencingListener extends DifferencingListener {
  def onMismatch(id: VersionID, lastUpdate:DateTime, upstreamVsn: String, downstreamVsn: String, origin:MatchOrigin, level:DifferenceFilterLevel) = null
  def onMatch(id: VersionID, vsn: String, origin:MatchOrigin) = null
}