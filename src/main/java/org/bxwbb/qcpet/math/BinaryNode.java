package org.bxwbb.qcpet.math;

record BinaryNode(ExpressionNode left, char operator, ExpressionNode right) implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        double leftValue = toDouble(left.evaluate(context));
        double rightValue = toDouble(right.evaluate(context));
        return switch (operator) {
            case '+' -> leftValue + rightValue;
            case '-' -> leftValue - rightValue;
            case '*' -> leftValue * rightValue;
            case '/' -> leftValue / rightValue;
            case '%' -> leftValue % rightValue;
            default -> throw new MathExpressionException("Unknown operator: " + operator);
        };
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new MathExpressionException("Value is not a number: " + value);
    }
}
