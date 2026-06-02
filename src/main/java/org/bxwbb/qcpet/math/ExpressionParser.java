package org.bxwbb.qcpet.math;

import java.util.ArrayList;
import java.util.List;

final class ExpressionParser {

    private final String expression;
    private int index;

    ExpressionParser(String expression) {
        this.expression = expression;
    }

    ExpressionNode parse() {
        ExpressionNode node = parseAdditive();
        skipWhitespace();
        if (!isEnd()) {
            throw error("Unexpected token");
        }
        return node;
    }

    private ExpressionNode parseAdditive() {
        ExpressionNode node = parseMultiplicative();
        while (true) {
            skipWhitespace();
            if (match('+')) {
                node = new BinaryNode(node, '+', parseMultiplicative());
            } else if (match('-')) {
                node = new BinaryNode(node, '-', parseMultiplicative());
            } else {
                return node;
            }
        }
    }

    private ExpressionNode parseMultiplicative() {
        ExpressionNode node = parseUnary();
        while (true) {
            skipWhitespace();
            if (match('*')) {
                node = new BinaryNode(node, '*', parseUnary());
            } else if (match('/')) {
                node = new BinaryNode(node, '/', parseUnary());
            } else if (matchOperatorPercent()) {
                node = new BinaryNode(node, '%', parseUnary());
            } else {
                return node;
            }
        }
    }

    private ExpressionNode parseUnary() {
        skipWhitespace();
        if (match('+')) {
            return new UnaryNode('+', parseUnary());
        }
        if (match('-')) {
            return new UnaryNode('-', parseUnary());
        }
        if (matchCast("(z)")) {
            return new CastNode(CastType.INTEGER, parseUnary());
        }
        if (matchCast("(r)")) {
            return new CastNode(CastType.REAL, parseUnary());
        }
        return parsePrimary();
    }

    private ExpressionNode parsePrimary() {
        skipWhitespace();
        if (match('(')) {
            ExpressionNode node = parseAdditive();
            expect(')');
            return node;
        }
        if (peek() == '%') {
            return parseArgument();
        }
        if (peek() == '"' || peek() == '\'') {
            return new StringNode(parseQuotedString());
        }
        if (isNumberStart()) {
            return new NumberNode(parseNumber());
        }
        if (isIdentifierStart(peek())) {
            return parseIdentifierValue();
        }
        throw error("Expected value");
    }

    private ExpressionNode parseArgument() {
        expect('%');
        int start = index;
        while (Character.isDigit(peek())) {
            index++;
        }
        if (start == index) {
            throw error("Expected argument index");
        }
        expect('%');
        return new ArgumentNode(Integer.parseInt(expression.substring(start, index - 1)));
    }

    private ExpressionNode parseIdentifierValue() {
        List<String> names = parseNames();
        String firstName = names.getFirst();
        if (names.size() == 1) {
            if ("true".equals(firstName)) {
                return new BooleanNode(true);
            }
            if ("false".equals(firstName)) {
                return new BooleanNode(false);
            }
            if ("null".equals(firstName)) {
                return new NullNode();
            }
        }
        skipWhitespace();
        if (match('(')) {
            return new StaticAccessNode(resolveMethodNames(names), parseArguments());
        }
        if (names.size() < 2) {
            throw error("Unknown identifier");
        }
        return new StaticAccessNode(names, null);
    }

    private List<String> resolveMethodNames(List<String> names) {
        if (names.size() == 1) {
            return List.of("java", "lang", "Math", names.getFirst());
        }
        return names;
    }

    private List<ExpressionNode> parseArguments() {
        List<ExpressionNode> arguments = new ArrayList<>();
        skipWhitespace();
        if (match(')')) {
            return arguments;
        }
        do {
            arguments.add(parseAdditive());
            skipWhitespace();
        } while (match(','));
        expect(')');
        return arguments;
    }

    private List<String> parseNames() {
        List<String> names = new ArrayList<>();
        names.add(parseIdentifier());
        while (match('.')) {
            names.add(parseIdentifier());
        }
        return names;
    }

    private String parseIdentifier() {
        skipWhitespace();
        if (!isIdentifierStart(peek())) {
            throw error("Expected identifier");
        }
        int start = index++;
        while (isIdentifierPart(peek())) {
            index++;
        }
        return expression.substring(start, index);
    }

    private double parseNumber() {
        int start = index;
        if (peek() == '.') {
            index++;
        }
        while (Character.isDigit(peek())) {
            index++;
        }
        if (peek() == '.') {
            index++;
            while (Character.isDigit(peek())) {
                index++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            index++;
            if (peek() == '+' || peek() == '-') {
                index++;
            }
            while (Character.isDigit(peek())) {
                index++;
            }
        }
        try {
            return Double.parseDouble(expression.substring(start, index));
        } catch (NumberFormatException exception) {
            throw error("Invalid number");
        }
    }

    private String parseQuotedString() {
        char quote = peek();
        expect(quote);
        StringBuilder builder = new StringBuilder();
        while (!isEnd() && peek() != quote) {
            char current = expression.charAt(index++);
            if (current == '\\') {
                builder.append(parseEscape());
            } else {
                builder.append(current);
            }
        }
        expect(quote);
        return builder.toString();
    }

    private char parseEscape() {
        if (isEnd()) {
            throw error("Unclosed string escape");
        }
        return switch (expression.charAt(index++)) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            default -> throw error("Unsupported string escape");
        };
    }

    private boolean matchCast(String value) {
        skipWhitespace();
        if (!expression.startsWith(value, index)) {
            return false;
        }
        index += value.length();
        return true;
    }

    private boolean matchOperatorPercent() {
        skipWhitespace();
        if (peek() != '%' || (index + 1 < expression.length() && Character.isDigit(expression.charAt(index + 1)))) {
            return false;
        }
        index++;
        return true;
    }

    private boolean isNumberStart() {
        return Character.isDigit(peek()) || (peek() == '.' && index + 1 < expression.length() && Character.isDigit(expression.charAt(index + 1)));
    }

    private boolean match(char expected) {
        skipWhitespace();
        if (peek() != expected) {
            return false;
        }
        index++;
        return true;
    }

    private void expect(char expected) {
        skipWhitespace();
        if (peek() != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    private void skipWhitespace() {
        while (!isEnd() && Character.isWhitespace(expression.charAt(index))) {
            index++;
        }
    }

    private boolean isEnd() {
        return index >= expression.length();
    }

    private char peek() {
        return isEnd() ? '\0' : expression.charAt(index);
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private MathExpressionException error(String message) {
        return new MathExpressionException(message + " at " + index + " in expression: " + expression);
    }
}
