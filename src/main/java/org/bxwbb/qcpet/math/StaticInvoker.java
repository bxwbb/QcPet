package org.bxwbb.qcpet.math;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

final class StaticInvoker {

    private StaticInvoker() {
    }

    static Object readStaticField(List<String> names) {
        StaticMember member = findMember(names);
        try {
            Field field = member.owner().getField(member.name());
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new MathExpressionException("Field is not static: " + String.join(".", names));
            }
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new MathExpressionException("Cannot read static field: " + String.join(".", names), exception);
        }
    }

    static Object invokeStaticMethod(List<String> names, List<Object> arguments) {
        StaticMember member = findMember(names);
        Method bestMethod = null;
        Object[] bestArguments = null;
        int bestScore = Integer.MAX_VALUE;
        for (Method method : member.owner().getMethods()) {
            if (!method.getName().equals(member.name()) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Object[] converted = convertArguments(method.getParameterTypes(), arguments);
            if (converted == null) {
                continue;
            }
            int score = conversionScore(method.getParameterTypes(), arguments);
            if (score < bestScore) {
                bestMethod = method;
                bestArguments = converted;
                bestScore = score;
            }
        }
        if (bestMethod == null) {
            throw new MathExpressionException("No matching static method: " + String.join(".", names));
        }
        try {
            return bestMethod.invoke(null, bestArguments);
        } catch (ReflectiveOperationException exception) {
            throw new MathExpressionException("Cannot invoke static method: " + String.join(".", names), exception);
        }
    }

    private static StaticMember findMember(List<String> names) {
        if (names.size() < 2) {
            throw new MathExpressionException("Static member needs class and member name");
        }
        String memberName = names.getLast();
        for (int i = names.size() - 1; i > 0; i--) {
            String className = String.join(".", names.subList(0, i));
            try {
                return new StaticMember(Class.forName(className), memberName);
            } catch (ClassNotFoundException ignored) {
                // Continue searching because inner package/class boundaries are ambiguous in source text.
            }
        }
        throw new MathExpressionException("Cannot find class for: " + String.join(".", names));
    }

    private static Object[] convertArguments(Class<?>[] parameterTypes, List<Object> arguments) {
        if (parameterTypes.length != arguments.size()) {
            return null;
        }
        Object[] converted = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object value = convertValue(parameterTypes[i], arguments.get(i));
            if (value == UnmatchedValue.INSTANCE) {
                return null;
            }
            converted[i] = value;
        }
        return converted;
    }

    private static Object convertValue(Class<?> targetType, Object value) {
        if (value == null) {
            return targetType.isPrimitive() ? UnmatchedValue.INSTANCE : null;
        }
        Class<?> boxedType = box(targetType);
        if (boxedType.isInstance(value)) {
            return value;
        }
        if (boxedType == String.class) {
            return value instanceof String ? value : UnmatchedValue.INSTANCE;
        }
        if (boxedType == Character.class) {
            String text = value instanceof String string ? string : null;
            return text != null && text.length() == 1 ? text.charAt(0) : UnmatchedValue.INSTANCE;
        }
        if (boxedType == Boolean.class) {
            return value instanceof Boolean ? value : UnmatchedValue.INSTANCE;
        }
        if (Number.class.isAssignableFrom(boxedType) && value instanceof Number number) {
            return convertNumber(boxedType, number);
        }
        return boxedType.isAssignableFrom(value.getClass()) ? value : UnmatchedValue.INSTANCE;
    }

    private static Object convertNumber(Class<?> boxedType, Number number) {
        if (boxedType == Byte.class) {
            return number.byteValue();
        }
        if (boxedType == Short.class) {
            return number.shortValue();
        }
        if (boxedType == Integer.class) {
            return number.intValue();
        }
        if (boxedType == Long.class) {
            return number.longValue();
        }
        if (boxedType == Float.class) {
            return number.floatValue();
        }
        if (boxedType == Double.class) {
            return number.doubleValue();
        }
        return UnmatchedValue.INSTANCE;
    }

    private static int conversionScore(Class<?>[] parameterTypes, List<Object> arguments) {
        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Object argument = arguments.get(i);
            if (argument == null) {
                score += parameterTypes[i].isPrimitive() ? 100 : 10;
                continue;
            }
            Class<?> parameterType = box(parameterTypes[i]);
            Class<?> argumentType = argument.getClass();
            if (parameterType.equals(argumentType)) {
                continue;
            }
            if (Number.class.isAssignableFrom(parameterType) && argument instanceof Number) {
                score += numericScore(parameterType, argumentType);
                continue;
            }
            score += parameterType.isAssignableFrom(argumentType) ? 5 : 50;
        }
        return score;
    }

    private static int numericScore(Class<?> parameterType, Class<?> argumentType) {
        if (argumentType == Double.class) {
            return parameterType == Double.class ? 0 : 20 + numericRank(parameterType);
        }
        if (argumentType == Float.class) {
            return parameterType == Float.class ? 0 : parameterType == Double.class ? 1 : 20 + numericRank(parameterType);
        }
        if (argumentType == Long.class) {
            return parameterType == Long.class ? 0 : parameterType == Double.class ? 2 : parameterType == Float.class ? 3 : 20 + numericRank(parameterType);
        }
        if (argumentType == Integer.class || argumentType == Short.class || argumentType == Byte.class) {
            return numericRank(parameterType);
        }
        return 50;
    }

    private static int numericRank(Class<?> type) {
        if (type == Byte.class) {
            return 1;
        }
        if (type == Short.class) {
            return 2;
        }
        if (type == Integer.class) {
            return 3;
        }
        if (type == Long.class) {
            return 4;
        }
        if (type == Float.class) {
            return 5;
        }
        if (type == Double.class) {
            return 6;
        }
        return 50;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private record StaticMember(Class<?> owner, String name) {
    }

    private enum UnmatchedValue {
        INSTANCE
    }
}
