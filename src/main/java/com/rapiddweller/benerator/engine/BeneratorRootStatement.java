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

package com.rapiddweller.benerator.engine;

import com.rapiddweller.benerator.Generator;
import com.rapiddweller.benerator.engine.statement.GenIterStatement;
import com.rapiddweller.benerator.engine.statement.GenIterTask;
import com.rapiddweller.benerator.engine.statement.IncludeStatement;
import com.rapiddweller.benerator.engine.statement.SequentialStatement;
import com.rapiddweller.benerator.engine.statement.StatementProxy;
import com.rapiddweller.benerator.factory.BeneratorExceptionFactory;
import com.rapiddweller.benerator.wrapper.NShotGeneratorProxy;
import com.rapiddweller.common.BeanUtil;
import com.rapiddweller.common.Visitor;
import com.rapiddweller.script.DatabeneScriptParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The root {@link Statement} for executing descriptor file based data generation.<br/><br/>
 * Created: 24.10.2009 11:08:46
 * @author Volker Bergmann
 * @since 0.6.0
 */
public class BeneratorRootStatement extends SequentialStatement {

  private final Map<String, String> attributes;

  public BeneratorRootStatement(Map<String, String> attributes) {
    this.attributes = new HashMap<>(attributes);
  }

  @Override
  public boolean execute(BeneratorContext context) {
    mapAttributesTo(context);
    if (context.isDefaultImports()) {
      context.importDefaults();
    }
    super.execute(context);
    return true;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Generator<?> getGenerator(String name, BeneratorContext context) {
    GenIterStatement statement = getGeneratorStatement(name, context);
    Generator<?> generator = new TaskBasedGenerator(statement.getTask());
    return new NShotGeneratorProxy(generator, statement.generateCount(context));
  }

  public GenIterStatement getGeneratorStatement(String name, BeneratorContext context) {
    BeneratorVisitor visitor = new BeneratorVisitor(name, context);
    accept(visitor);
    GenIterStatement statement = visitor.getResult();
    if (statement == null) {
      throw BeneratorExceptionFactory.getInstance().illegalArgument("Generator not found: " + name);
    }
    return statement;
  }

  protected void mapAttributesTo(BeneratorContext context) {
    for (Entry<String, String> attribute : attributes.entrySet()) {
      String key = attribute.getKey();
      Object value = attribute.getValue();
      Object result;
      if ("generatorFactory".equals(key)) {
        result = DatabeneScriptParser.parseBeanSpec(value.toString()).evaluate(context);
      } else {
        result = value;
      }
      BeanUtil.setPropertyValue(context, key, result, true, true);
    }
  }

  static class BeneratorVisitor implements Visitor<Statement> {

    private final String name;
    private final BeneratorContext context;
    private GenIterStatement result;

    public BeneratorVisitor(String name, BeneratorContext context) {
      this.name = name;
      this.context = context;
    }

    public GenIterStatement getResult() {
      return result;
    }

    @Override
    public void visit(Statement statement) {
      if (result != null) {
        return;
      }
      if (statement instanceof GenIterStatement) {
        GenIterStatement generatorStatement = (GenIterStatement) statement;
        GenIterTask target = generatorStatement.getTask();
        if (name.equals(target.getTaskName())) {
          result = generatorStatement;
        }
      } else if (statement instanceof StatementProxy) {
        visit(((StatementProxy) statement).getRealStatement(context));
      } else if (statement instanceof IncludeStatement) {
        String uri = ((IncludeStatement) statement).getUri().evaluate(context);
        if (uri != null && uri.toLowerCase().endsWith(".xml")) {
          DescriptorRunner descriptorRunner = new DescriptorRunner(context.resolveRelativeUri(uri), context);
          BeneratorRootStatement rootStatement = descriptorRunner.parseDescriptorFile();
          result = rootStatement.getGeneratorStatement(name, context);
        }
      } else if (!(statement instanceof BeneratorRootStatement)) {
        statement.execute(context);
      }
    }
  }

  @Override
  public String toString() {
    return "root";
  }

}
