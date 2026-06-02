package org.bxwbb.qcpet.math;

record BooleanNode(boolean value) implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        return value;
    }
}
