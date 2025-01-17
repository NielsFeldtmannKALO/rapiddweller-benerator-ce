/*
 * (c) Copyright 2006-2020 by rapiddweller GmbH & Volker Bergmann. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, is permitted under the terms of the
 * GNU General Public License.
 *
 * For redistributing this software or a derivative work under a license other
 * than the GPL-compatible Free Software License as defined by the Free
 * Software Foundation or approved by OSI, you must first obtain a commercial
 * license to this software product from rapiddweller GmbH & Volker Bergmann.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * WITHOUT A WARRANTY OF ANY KIND. ALL EXPRESS OR IMPLIED CONDITIONS,
 * REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE
 * HEREBY EXCLUDED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.rapiddweller.platform.dbunit;

import com.rapiddweller.common.exception.ExceptionFactory;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Provides utility methods for DbUnit files.<br/><br/>
 * Created: 21.09.2011 15:09:03
 * @author Volker Bergmann
 * @since 0.7.1
 */
public class DbUnitUtil {

  private DbUnitUtil() {
    // private constructor to prevent instantiation
  }

  public static void skipRootElement(XMLStreamReader reader) {
    try {
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT &&
            "dataset".equals(reader.getLocalName())) {
          return;
        }
      }
    } catch (XMLStreamException e) {
      throw ExceptionFactory.getInstance().internalError("Error skipping root element", e);
    }
  }

  public static void skipNonStartTags(XMLStreamReader reader) {
    // skip non-start-tags
    try {
      while (reader != null && reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) {
        // empty loop
        if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
          System.out.println(reader.getLocalName());
        }
      }
    } catch (XMLStreamException e) {
      throw ExceptionFactory.getInstance().internalError("Error processing XML", e);
    }
  }

}
