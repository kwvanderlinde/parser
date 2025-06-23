/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * RPTools Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import net.rptools.parser.function.AbstractFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

public class EvaluationTest {
  private Parser parser;
  private MapVariableResolver resolver;

  @BeforeEach
  public void setUp() throws ParserException {
    parser = new Parser();
    parser.addFunction(new NonDeterministicIdentityFunction());
    parser.addFunction(new DeterministicIdentityFunction());
    parser.addFunction(new IncrementFunction());

    resolver = new MapVariableResolver();
    resolver.setVariable("ii", new BigDecimal(100));
    resolver.setVariable("C_mpl.x", new BigDecimal(42));
    resolver.setVariable("foo", VariableModifiers.Prompt, new BigDecimal(10));
    resolver.setVariable("quotedValue", "\"value\"");
    resolver.setVariable("x", List.of("one", "two"));
  }

  @Test
  public void testAssignToTrue() {
    assertThrows(
        ParserException.class,
        () -> evaluateExpression(parser, resolver, false, "true = 2", BigDecimal.valueOf(2)));
  }

  @Test
  public void testAssignToFalse() {
    assertThrows(
        ParserException.class,
        () -> evaluateExpression(parser, resolver, false, "false = 2", BigDecimal.valueOf(2)));
  }

  @Test
  public void testAssignment() throws ParserException {
    evaluateExpression(parser, resolver, false, "a = 5", new BigDecimal(5));
    assertEquals(new BigDecimal(5), resolver.getVariable("a"));

    evaluateExpression(parser, resolver, false, "b = a * 2", new BigDecimal(10));
    assertEquals(new BigDecimal(10), resolver.getVariable("b"));

    evaluateExpression(parser, resolver, false, "b = b * b", new BigDecimal(100));
    assertEquals(new BigDecimal(100), resolver.getVariable("b"));

    evaluateExpression(parser, resolver, false, "10 * set(\"c\", 10)", new BigDecimal(100));
    assertEquals(new BigDecimal(10), resolver.getVariable("c"));
  }

  @ParameterizedTest(name = "{0}; {1}; {2}")
  @CsvFileSource(
      resources = "EvaluationTest.testSuccessfulEvaluations.csv",
      numLinesToSkip = 1,
      delimiter = ';',
      quoteCharacter = '`',
      ignoreLeadingAndTrailingWhitespace = false)
  public void testSuccessfulEvaluations(String label, String input, Object expectedValue)
      throws ParserException {
    // Cast expectation to BigDecimal
    if (expectedValue instanceof String s) {
      try {
        expectedValue = new BigDecimal(s);
      } catch (NumberFormatException e) {
        // Ignore. It's just not a number, okay?
      }
    }

    evaluateExpression(parser, resolver, false, input, expectedValue);
  }

  @ParameterizedTest(name = "{0}; {1}; {2}")
  @CsvFileSource(
      resources = "EvaluationTest.testSuccessfulDeterministicEvaluations.csv",
      numLinesToSkip = 1,
      delimiter = ';',
      quoteCharacter = '`',
      ignoreLeadingAndTrailingWhitespace = false)
  public void testSuccessfulDeterministicEvaluations(
      String label, String input, Object expectedValue) throws ParserException {
    // Cast expectation to BigDecimal
    if (expectedValue instanceof String s) {
      try {
        expectedValue = new BigDecimal(s);
      } catch (NumberFormatException e) {
        // Ignore. It's just not a number, okay?
      }
    }

    evaluateExpression(parser, resolver, true, input, expectedValue);
  }

  private void evaluateExpression(
      Parser p, VariableResolver r, boolean makeDeterministic, String expression, Object answer)
      throws ParserException {

    var ast = p.parseExpression(expression);
    if (makeDeterministic) {
      ast = ast.getDeterministicExpression(resolver);
    }

    Object result = ast.evaluate(r);

    if (answer instanceof BigDecimal bd) {
      assertInstanceOf(BigDecimal.class, result, "%s is also a BigDecimal");
      assertEquals(
          0,
          bd.compareTo((BigDecimal) result),
          String.format(
              "%s evaluated incorrectly expected <%s> but was <%s>", expression, answer, result));
    } else {
      assertEquals(
          answer,
          result,
          String.format(
              "%s evaluated incorrectly expected <%s> but was <%s>", expression, answer, result));
    }
  }

  private static class DeterministicIdentityFunction extends AbstractFunction {
    public DeterministicIdentityFunction() {
      super(1, 1, true, "deterministicIdentity");
    }

    @Override
    public Object childEvaluate(
        Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) {
      return parameters.get(0);
    }
  }

  private static class NonDeterministicIdentityFunction extends AbstractFunction {
    public NonDeterministicIdentityFunction() {
      super(1, 1, false, "nondeterministicIdentity");
    }

    @Override
    public Object childEvaluate(
        Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) {
      return parameters.get(0);
    }
  }

  private static class IncrementFunction extends AbstractFunction {
    public IncrementFunction() {
      super(1, 1, "increment");
    }

    @Override
    public Object childEvaluate(
        Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) {
      BigDecimal value = (BigDecimal) parameters.get(0);
      return value.add(BigDecimal.ONE);
    }
  }
}
