package org.bxwbb.qcpet.math;

public class MathExpression {

    private ExpressionNode expression = new NumberNode(0D);

    public Object calculate(Object... arguments) {
        return expression.evaluate(new EvaluationContext(arguments));
    }

    public double calculateDouble(Object... arguments) {
        Object result = calculate(arguments);
        if (result instanceof Number number) {
            return number.doubleValue();
        }
        throw new MathExpressionException("Expression result is not a number: " + result);
    }

    public void parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new MathExpressionException("Expression cannot be blank");
        }
        this.expression = new ExpressionParser(expression).parse();
    }
}
