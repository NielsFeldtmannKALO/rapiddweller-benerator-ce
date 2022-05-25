/*
 * (c) Copyright 2006-2022 by rapiddweller GmbH & Volker Bergmann. All rights reserved.
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

package com.rapiddweller.platform.db;

import com.rapiddweller.benerator.engine.BeneratorMonitor;
import com.rapiddweller.benerator.primitive.datetime.CurrentDateTimeGenerator;
import com.rapiddweller.benerator.test.AbstractBeneratorIntegrationTest;
import com.rapiddweller.benerator.test.ConsumerMock;
import com.rapiddweller.common.CollectionUtil;
import com.rapiddweller.common.TimeUtil;
import com.rapiddweller.common.exception.ServiceFailedException;
import com.rapiddweller.jdbacl.DBUtil;
import com.rapiddweller.jdbacl.dialect.HSQLUtil;
import com.rapiddweller.model.data.Entity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration test for Benerator's database support.<br/><br/>
 * Created: 24.05.2010 17:52:58
 * @author Volker Bergmann
 * @since 0.6.2
 */
@SuppressWarnings("CheckStyle")
public class DatabaseIntegrationTest extends AbstractBeneratorIntegrationTest {

  private final String folder = getClass().getPackageName().replace('.', '/');

  private String dbUrl;
  private DefaultDBSystem db;
  private ConsumerMock consumer;

  @Before
  public void setUpDatabase() {
    DBUtil.resetMonitors();
    consumer = new ConsumerMock(true);
    context.setGlobal("cons", consumer);
    dbUrl = HSQLUtil.getInMemoryURL(getClass().getSimpleName());
    db = new DefaultDBSystem("db", dbUrl, HSQLUtil.DRIVER,
        HSQLUtil.DEFAULT_USER, HSQLUtil.DEFAULT_PASSWORD, context.getDataModel());
    db.setSchema("PUBLIC");
    db.execute("drop table referer if exists");
    db.execute("drop table REFEREE if exists");
    db.execute("create table referee (" +
        "id int, " +
        "name varchar(20) not null, " +
        "n int default 1 not null, " +
        "primary key (id))");
    db.execute("insert into REFEREE (id, name, n) values (2, 'Alice', 2)");
    db.execute("insert into REFEREE (id, name, n) values (3, 'Bob', 3)");
    db.execute(
        "create table referer ( " +
            "   id int," +
            "   referee_id int," +
            "   the_date date," +
            "   primary key (id)," +
            "   constraint referee_fk foreign key (referee_id) references referee (id))");
    context.setGlobal("db", db);
    context.getDataModel().addDescriptorProvider(db);
  }

  @After
  public void shutDown() {
    HSQLUtil.shutdown(dbUrl, HSQLUtil.DEFAULT_USER, HSQLUtil.DEFAULT_PASSWORD);
  }


  // generation of database references -------------------------------------------------------------------------------

  @Test
  public void testScriptResolution() {
    context.setGlobal("tblName", "referee");
    parseAndExecuteXmlString("<evaluate id='refCount' target='db'>{'select count(*) from ' + tblName}</evaluate>");
    assertEquals(2, ((Number) context.get("refCount")).intValue());
    closeAndCheckCleanup();
  }

  @Test
  public void testDefaultColumnValue() {
    parseAndExecuteXmlString("<generate type='referee' count='3' consumer='cons'/>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      assertEquals(1, product.get("n"));
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_default_nullable() {
    parseAndExecuteXmlString("<generate type='referer' count='3' consumer='cons'/>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      assertNull(product.get("referee_id"));
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_default_not_null_defaultOneToOne() {
    context.setDefaultOneToOne(true);
    parseAndExecuteXmlString(
        "<generate type='referer' count='unbounded' consumer='cons'>" +
            "  <reference name='referee_id' nullable='false' source='db' targetType='REFEREE'/>" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    for (Entity product : products) {
      int ref = (Integer) product.get("referee_id");
      assertTrue(ref == 2 || ref == 3);
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_default_not_null_defaultManyToOne() {
    context.setDefaultOneToOne(false);
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <reference name='referee_id' nullable='false' source='db' targetType='REFEREE'/>" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      int ref = (Integer) product.get("referee_id");
      assertTrue(ref == 2 || ref == 3);
    }
    closeAndCheckCleanup();
  }


  @Test
  public void testDbRef_distribution() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <reference name='referee_id' targetType='REFEREE' source='db' " +
            "    distribution='new com.rapiddweller.benerator.distribution.function.ExponentialFunction(-0.5)' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      int ref = (Integer) product.get("referee_id");
      assertTrue(ref == 2 || ref == 3);
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_values() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <reference name='referee_id' values='1' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      assertEquals(1, product.get("referee_id"));
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_constant() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <reference name='referee_id' constant='1' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      assertEquals(1, product.get("referee_id"));
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_constant_script() {
    long c0 = BeneratorMonitor.INSTANCE.getTotalGenerationCount();
    context.setGlobal("rid", 2);
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <reference name='referee_id' constant='{rid}' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      assertEquals(2, product.get("referee_id"));
    }
    closeAndCheckCleanup();
    assertTrue(BeneratorMonitor.INSTANCE.getTotalGenerationCount() - c0 >= 3);
  }

  @Test
  public void testDbRef_attribute_constant_script() {
    context.setGlobal("rid", 2);
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <attribute name='referee_id' constant='{rid}' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      assertEquals(2, product.get("referee_id"));
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_script() {
    context.setGlobal("rid", 2);
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <reference name='referee_id' script='rid + 1' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity product : products) {
      assertEquals(3, product.get("referee_id"));
    }
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_explicit_selector() {
    context.setGlobal("key", 2);
    parseAndExecuteXmlString(
        "<generate type='referer' count='unbounded' consumer='cons'>" +
            "  <reference name='referee_id' source='db' " +
            "     selector=\"{ftl:select id from referee where id=${key}}\" " +
            "     nullable='false'/>" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(1, products.size());
    assertEquals(2, products.get(0).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_explicit_subSelector() {
    context.setGlobal("key", 2);
    parseAndExecuteXmlString(
        "<generate type='referer' consumer='cons' count='3'>" +
            "  <reference name='referee_id' source='db' " +
            "     subSelector='{ftl:select id from referee order by id}' " +
            "     nullable='false'/>" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    assertEquals(2, products.get(0).get("referee_id"));
    assertEquals(2, products.get(1).get("referee_id"));
    assertEquals(2, products.get(2).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDbRef_entity_selector() {
    context.setGlobal("key", 2);
    parseAndExecuteXmlString(
        "<generate type='referer' count='unbounded' consumer='cons'>" +
            "  <reference name='referee_id' source='db' " +
            "     selector='{ftl:id=${key}}' " +
            "     nullable='false'/>" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(1, products.size());
    assertEquals(2, products.get(0).get("referee_id"));
    closeAndCheckCleanup();
  }


  // iteration through database entries ------------------------------------------------------------------------------

  @Test
  public void testDb_iterate_this() {
    parseAndExecuteXmlString(
        "<iterate type='referee' source='db' consumer='cons'>" +
            "  <attribute name='n' source='db' " +
            "     selector=\"{{'select n+1 from referee where id = ' + this.id}}\" cyclic='true' />" +
            "</iterate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("n"));
    assertEquals(4, products.get(1).get("n"));
    closeAndCheckCleanup();
  }


  // date generation -------------------------------------------------------------------------------------------------

  @Test
  public void test_datetime_resolution() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='3' consumer='cons'>" +
            "  <attribute name='the_date' generator='" + CurrentDateTimeGenerator.class.getName() + "' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(3, products.size());
    for (Entity entity : products) {
      assertTrue(TimeUtil.isNow(((Date) entity.get("the_date")).getTime(), 2000));
    }
    closeAndCheckCleanup();
  }

  // transaction control ---------------------------------------------------------------------------------------------

  @Test
  public void testTx_default() {
    context.setDefaultOneToOne(false);
    db.execute("delete from referee");
    parseAndExecuteXmlString(
        "<generate type='referee' count='2' consumer='cons'>" +
            "   <generate type='referer' count='2' consumer='cons'>" +
            "   </generate>" +
            "</generate>");
    // check generated products
    List<Entity> products = getConsumedEntities();
    assertEquals(6, products.size());
    // check transactions
    List<String> expectedInvocations = CollectionUtil.toList(
        ConsumerMock.START_CONSUMING, // referee #1
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #1
        ConsumerMock.FLUSH,
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #2
        ConsumerMock.FLUSH,
        ConsumerMock.FINISH_CONSUMING, ConsumerMock.FLUSH, // referee #1
        ConsumerMock.START_CONSUMING, // referee #2
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #3
        ConsumerMock.FLUSH,
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #4
        ConsumerMock.FLUSH,
        ConsumerMock.FINISH_CONSUMING, ConsumerMock.FLUSH // referee #2
    );
    assertEquals(expectedInvocations, consumer.invocations);

    closeAndCheckCleanup();
  }

  @Test
  public void testTx_subPageSize0() {
    context.setDefaultOneToOne(false);
    db.execute("delete from referee");
    parseAndExecuteXmlString(
        "<generate type='referee' count='2' consumer='cons'>" +
            "   <generate type='referer' count='2' pageSize='0' consumer='cons'>" +
            "   </generate>" +
            "</generate>");
    // check generated products
    List<Entity> products = getConsumedEntities();
    assertEquals(6, products.size());
    // check transactions
    List<String> expectedInvocations = CollectionUtil.toList(
        ConsumerMock.START_CONSUMING, // referee #1
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #1
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #2
        ConsumerMock.FINISH_CONSUMING, ConsumerMock.FLUSH, // referee #1
        ConsumerMock.START_CONSUMING, // referee #2
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #3
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #4
        ConsumerMock.FINISH_CONSUMING, ConsumerMock.FLUSH // referee #2
    );
    assertEquals(expectedInvocations, consumer.invocations);

    closeAndCheckCleanup();
  }

  @Test
  public void testTx_allPageSizes0() {
    context.setDefaultOneToOne(false);
    db.execute("delete from referee");
    parseAndExecuteXmlString(
        "<generate type='referee' count='2' pageSize='0' consumer='cons'>" +
            "   <generate type='referer' count='2' pageSize='0' consumer='cons'>" +
            "   </generate>" +
            "</generate>");
    // check generated products
    List<Entity> products = getConsumedEntities();
    assertEquals(6, products.size());
    // check transactions
    List<String> expectedInvocations = CollectionUtil.toList(
        ConsumerMock.START_CONSUMING, // referee #1
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #1
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #2
        ConsumerMock.FINISH_CONSUMING, // referee #1
        ConsumerMock.START_CONSUMING, // referee #2
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #3
        ConsumerMock.START_CONSUMING, ConsumerMock.FINISH_CONSUMING, // referer #4
        ConsumerMock.FINISH_CONSUMING // referee #2
    );
    assertEquals(expectedInvocations, consumer.invocations);

    closeAndCheckCleanup();
  }


  // selector resolution ---------------------------------------------------------------------------------------------

  @Test
  public void testStaticEntitySelector_partial() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='e' type='REFEREE' source='db' subSelector='id=3' />" +
            "  <reference name='referee_id' script='e.id' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("referee_id"));
    assertEquals(3, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testStaticEntitySelector_complete() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='e' type='referee' source='db' subSelector='select * from referee where id=3' />" +
            "  <reference name='referee_id' script='e.id' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("referee_id"));
    assertEquals(3, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDynamicEntitySelector_partial() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='n' type='int' min='2' max='3' " +
            "    distribution='new com.rapiddweller.benerator.distribution.sequence.StepSequence(-1)'/>" +
            "  <variable name='e' type='REFEREE' source='db' subSelector=\"{{'id=' + n}}\" />" +
            "  <reference name='referee_id' script='e.id' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("referee_id"));
    assertEquals(2, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDynamicEntitySelector_complete() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='n' type='int' min='2' max='3' " +
            "    distribution='new com.rapiddweller.benerator.distribution.sequence.StepSequence(-1)'/>" +
            "  <variable name='e' type='referee' source='db' subSelector=\"{{'select * from referee where id=' + n}}\" />" +
            "  <reference name='referee_id' script='e.id' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("referee_id"));
    assertEquals(2, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testStaticArraySelector() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='a' source='db' subSelector='select id, n from referee where n=3' />" +
            "  <reference name='referee_id' script='a[0]' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("referee_id"));
    assertEquals(3, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDynamicArraySelector() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='n' type='int' min='2' max='3' distribution='new com.rapiddweller.benerator.distribution.sequence.StepSequence(-1)'/>" +
            "  <variable name='a' source='db' subSelector=\"{{'select id, n from referee where id=' + n}}\"/>" +
            "  <reference name='referee_id' script='a[0]' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("referee_id"));
    assertEquals(2, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testStaticValueSelector() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='v' type='int' min='2' distribution='increment' />" +
            "  <reference name='referee_id' source='db' " +
            "     subSelector='select n from referee where id=3' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(3, products.get(0).get("referee_id"));
    assertEquals(3, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDynamicValueSelectorUsingContextValue() {
    context.setGlobal("key", 2);
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <id name='id' type='int' min='2' distribution='increment' />" +
            "  <reference name='referee_id' source='db' " +
            "     subSelector=\"{{'select n from referee where id=' + key}}\" />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(2, products.get(0).get("referee_id"));
    assertEquals(2, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDynamicValueSelectorUsingVariable() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <variable name='v' type='int' min='2' distribution='increment' />" +
            "  <reference name='referee_id' source='db' " +
            "     subSelector=\"{{'select n from referee where id=' + v}}\" />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(2, products.get(0).get("referee_id"));
    assertEquals(3, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testDynamicValueSelectorUsingAttribute() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <id name='id' type='int' min='2' distribution='increment' />" +
            "  <reference name='referee_id' source='db' " +
            "     subSelector=\"{{'select n from referee where id=' + referer.id}}\" />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(2, products.get(0).get("referee_id"));
    assertEquals(3, products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testStaticValueSelectorWithEmptyResultSet() {
    parseAndExecuteXmlString(
        "<generate type='referer' maxCount='2' consumer='cons'>" +
            "  <reference name='referee_id' source='db' " +
            "     subSelector='select id from referee where id=5' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(0, products.size());
    closeAndCheckCleanup();
  }

  @Test
  public void testStaticValueSelectorWithNullResult() {
    parseAndExecuteXmlString(
        "<generate type='referer' count='2' consumer='cons'>" +
            "  <attribute name='referee_id' source='db' " +
            "     subSelector='select null from referee where id=2' />" +
            "</generate>");
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertNull(products.get(0).get("referee_id"));
    assertNull(products.get(1).get("referee_id"));
    closeAndCheckCleanup();
  }

  @Test
  public void testUpdater() throws Exception {
    parseAndExecuteXmlString(
        "<iterate type='referee' source='db' consumer='db.updater(), cons'>" +
            "  <attribute name='n' constant='9' />" +
            "</iterate>");
    // check entities sent to consumers
    List<Entity> products = getConsumedEntities();
    assertEquals(2, products.size());
    assertEquals(9, products.get(0).get("n"));
    assertEquals(9, products.get(1).get("n"));
    // check entities in database
    List<Object[]> storedData = DBUtil.query("select id, n from referee order by id", db.getConnection());
    assertEquals(2, storedData.size());
    assertEqualArrays(new Object[] {2, 9}, storedData.get(0));
    assertEqualArrays(new Object[] {3, 9}, storedData.get(1));
    closeAndCheckCleanup();
  }

  @Test(expected = ServiceFailedException.class)
  public void testUpdateOnNonExistingPK() {
    parseAndExecuteXmlString(
        "<iterate type='referee' source='db' selector='id=2' consumer='db.updater(), cons'>" +
            "  <attribute name='id' constant='11' />" +
            "</iterate>");
  }

  @Test
  public void testMetaDataCache_separated() {
    long c0 = BeneratorMonitor.INSTANCE.getTotalGenerationCount();
    db.setMetaCache(true);
    try {
      parseAndExecuteXmlString("<execute target='db'>drop table meta_test;</execute>");
    } catch (Exception e) {
      // this is expected
    }
    parseAndExecuteXmlString("<execute target='db'>" +
        "create table meta_test ( id int, text varchar(20), primary key (id));" +
        "</execute>");
    parseAndExecuteXmlString("<generate type='meta_test' count='5' consumer='db'/>");
    assertTrue(BeneratorMonitor.INSTANCE.getTotalGenerationCount() - c0 >= 5);
  }

  @Test
  public void testMetaDataCache_in_file() {
    long c0 = BeneratorMonitor.INSTANCE.getTotalGenerationCount();
    parseAndExecuteFile(folder + "/metaCache.ben.xml");
    assertTrue(BeneratorMonitor.INSTANCE.getTotalGenerationCount() - c0 >= 5);
  }

  // private helpers -------------------------------------------------------------------------------------------------

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected List<Entity> getConsumedEntities() {
    return (List) consumer.getProducts();
  }

  private void closeAndCheckCleanup() {
    context.close();
    db.close();
    DBUtil.assertAllDbResourcesClosed(true);
  }

}
