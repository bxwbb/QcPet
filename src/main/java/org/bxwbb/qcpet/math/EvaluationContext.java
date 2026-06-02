package org.bxwbb.qcpet.math;

import java.util.Arrays;

final class EvaluationContext {

    private final Object[] arguments;

    EvaluationContext(Object[] arguments) {
        this.arguments = arguments == null ? new Object[0] : Arrays.copyOf(arguments, arguments.length);
    }

    Object getArgument(int index) {
        if (index < 1 || index > arguments.length) {
            throw new MathExpressionException("Argument %" + index + "% does not exist");
        }
        return arguments[index - 1];
    }
}
