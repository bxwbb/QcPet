package org.bxwbb.qcpet.math;

record ArgumentNode(int index) implements ExpressionNode {

    @Override
    public Object evaluate(EvaluationContext context) {
        return context.getArgument(index);
    }
}
