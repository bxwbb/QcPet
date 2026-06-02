package org.bxwbb.qcpet.math;

final class NullNode implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        return null;
    }
}
