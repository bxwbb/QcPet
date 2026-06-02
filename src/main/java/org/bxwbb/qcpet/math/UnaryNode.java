package org.bxwbb.qcpet.math;

record UnaryNode(char operator, ExpressionNode value) implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        Object result = value.evaluate(context);
        if (!(result instanceof Number number)) {
            throw new MathExpressionException("Value is not a number: " + result);
        }
        double numberValue = number.doubleValue();
        return operator == '-' ? -numberValue : numberValue;
    }
}
