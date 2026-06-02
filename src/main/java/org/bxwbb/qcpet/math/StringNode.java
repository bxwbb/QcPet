package org.bxwbb.qcpet.math;

record StringNode(String value) implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        return value;
    }
}
