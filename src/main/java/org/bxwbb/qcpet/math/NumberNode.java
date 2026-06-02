package org.bxwbb.qcpet.math;

record NumberNode(double value) implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        return value;
    }
}
