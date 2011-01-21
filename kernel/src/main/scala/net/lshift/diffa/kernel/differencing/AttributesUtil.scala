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

/**
 * Utility for working with attribute maps.
 */
object AttributesUtil {
  def toSeq(attrs:Map[String, String]):Seq[String] = {
    attrs.toSeq.sortBy { case (name, value) => name }.map { case (name, value) => value }
  }

  def toMap(keys:Iterable[String], attrs:Iterable[String]):Map[String, String] = toMap(keys.toSeq, attrs.toSeq)
  def toMap(keys:Seq[String], attrs:Seq[String]):Map[String, String] = (keys.sorted, attrs).zip.toMap
}