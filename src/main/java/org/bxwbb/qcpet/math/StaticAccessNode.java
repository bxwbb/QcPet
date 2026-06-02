package org.bxwbb.qcpet.math;

import java.util.ArrayList;
import java.util.List;

record StaticAccessNode(List<String> names, List<ExpressionNode> arguments) implements ExpressionNode {

    StaticAccessNode {
        names = List.copyOf(names);
        arguments = arguments == null ? null : List.copyOf(arguments);
    }

    @Override
    public Object evaluate(EvaluationContext context) {
        List<Object> values = new ArrayList<>();
        if (arguments != null) {
            for (ExpressionNode argument : arguments) {
                values.add(argument.evaluate(context));
            }
        }
        return arguments == null
                ? StaticInvoker.readStaticField(names)
                : StaticInvoker.invokeStaticMethod(names, values);
    }
}
