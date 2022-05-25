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

package com.rapiddweller.benerator.engine.parser.xml;

import com.rapiddweller.benerator.engine.Statement;
import com.rapiddweller.benerator.test.AbstractBeneratorIntegrationTest;
import com.rapiddweller.common.exception.SyntaxError;
import com.rapiddweller.common.xml.XMLUtil;
import org.junit.Test;
import org.w3c.dom.Element;

import static org.junit.Assert.assertEquals;

/**
 * Tests the {@link IfParser}.<br/><br/>
 * Created: 19.02.2010 09:47:36
 * @author Volker Bergmann
 * @since 0.6.0
 */
public class IfParserAndStatementTest extends AbstractBeneratorIntegrationTest {

  @Test
  public void testPlainTrue() {
    parseAndExecuteXmlString("<if test='1==1'><setting name='executed' value='OK'/></if>");
    assertEquals("OK", context.getGlobal("executed"));
  }

  @Test
  public void testPlainFalse() {
    executeAndCheckGlobal("<if test='2==3'><setting name='executed' value='OK'/></if>", null);
  }

  @Test
  public void testThenTrue() {
    executeAndCheckGlobal("<if test='1==1'><then><setting name='executed' value='OK'/></then></if>", "OK");
  }

  @Test
  public void testThenFalse() {
    executeAndCheckGlobal("<if test='2==3'><then><setting name='executed' value='OK'/></then></if>", null);
  }

  private void executeAndCheckGlobal(String xml, String expectedGlobal) {
    parseAndExecuteXmlString(xml);
    assertEquals(expectedGlobal, context.getGlobal("executed"));
  }

  @Test
  public void testThenElse() {
    Statement statement = parseXmlString(
        "<if test='x==3'>" +
            "	<then>" +
            "		<setting name='executed' value='OK'/>" +
            "	</then>" +
            "	<else>" +
            "		<setting name='executed' value='NOK'/>" +
            "	</else>" +
            "</if>"
    );
    // test 'then' part
    context.setGlobal("x", 3);
    statement.execute(context);
    assertEquals("OK", context.getGlobal("executed"));
    // test 'else' part
    context.setGlobal("x", 2);
    statement.execute(context);
    assertEquals("NOK", context.getGlobal("executed"));
  }

  @Test(expected = SyntaxError.class)
  public void testElseWithoutIf() {
    parseAndExecuteXmlString("<if test='2==3'><else/></if>");
  }

  @Test(expected = SyntaxError.class)
  public void testTwoThens() {
    assertSyntaxError("<if test='2==3'><then/><then/></if>");
  }

  @Test(expected = SyntaxError.class)
  public void testTwoElses() {
    assertSyntaxError("<if test='2==3'><then/><else/><else/></if>");
  }

  @Test(expected = SyntaxError.class)
  public void testThenAfterElse() {
    assertSyntaxError("<if test='2==3'><else/><then/><else/></if>");
  }

  @Test(expected = SyntaxError.class)
  public void testBeepBeforeThen() {
    assertSyntaxError("<if test='2==3'><beep/><then><setting name='executed' value='OK'/></then></if>");
  }

  public void assertSyntaxError(String xml) {
    Element element = XMLUtil.parseStringAsElement(xml);
    new IfParser().parse(element, null, null, null);
  }

}
