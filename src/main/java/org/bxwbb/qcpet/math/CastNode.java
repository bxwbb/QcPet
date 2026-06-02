package org.bxwbb.qcpet.math;

record CastNode(CastType type, ExpressionNode value) implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        Object result = value.evaluate(context);
        if (!(result instanceof Number number)) {
            throw new MathExpressionException("Cannot cast non-number value: " + result);
        }
        if (type == CastType.INTEGER) {
            return number.intValue();
        }
        return number.doubleValue();
    }
}
