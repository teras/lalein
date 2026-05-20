package com.panayotis.lalein;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a single CLDR plural rule expression against a numeric input.
 * Implements the syntax defined in UTS#35 §6.2.1 (Language Plural Rules):
 * <pre>
 *   condition  = and_condition (or and_condition)*
 *   and_cond   = relation (and relation)*
 *   relation   = expr (is | is not | in | not in | = | != | within | not within) range_list
 *   expr       = operand (% int)?
 *   operand    = n | i | v | w | f | t | c | e
 *   range_list = range (, range)*
 *   range      = int (.. int)?
 * </pre>
 * Operands per spec:
 *   n - absolute value of source
 *   i - integer digits of n
 *   v - number of visible fraction digits with trailing zeros
 *   w - number of visible fraction digits without trailing zeros
 *   f - visible fraction digits with trailing zeros, as integer
 *   t - visible fraction digits without trailing zeros, as integer
 *   c, e - compact decimal exponent (always 0 for plain numeric input)
 */
class CldrRuleEvaluator {

    static class Operands {
        final double n;
        final long i;
        final int v, w;
        final long f, t;
        final int c, e;

        Operands(double source) {
            double abs = source < 0 ? -source : source;
            this.n = abs;
            // Use Double.toString to avoid binary→decimal noise (BigDecimal.valueOf does the same).
            BigDecimal bd = new BigDecimal(Double.toString(abs));
            BigInteger intPart = bd.toBigInteger();
            this.i = intPart.longValue();
            BigDecimal frac = bd.subtract(new BigDecimal(intPart));
            if (frac.signum() == 0) {
                this.v = 0; this.w = 0; this.f = 0; this.t = 0;
            } else {
                BigDecimal stripped = frac.stripTrailingZeros();
                int scale = stripped.scale();
                long val = stripped.movePointRight(scale).longValue();
                this.v = scale; this.w = scale; this.f = val; this.t = val;
            }
            this.c = 0; this.e = 0;
        }

        double operand(String name) {
            switch (name) {
                case "n": return n;
                case "i": return i;
                case "v": return v;
                case "w": return w;
                case "f": return f;
                case "t": return t;
                case "c": case "e": return c;
            }
            throw new IllegalArgumentException("unknown operand: " + name);
        }
    }

    /** Returns true if the given rule body matches the operands. Strips
     *  trailing {@code @integer}/{@code @decimal} sample lists first. */
    static boolean evaluate(String rule, Operands op) {
        int at = rule.indexOf('@');
        if (at >= 0) rule = rule.substring(0, at);
        rule = rule.trim();
        if (rule.isEmpty()) return false;
        Parser p = new Parser(tokenize(rule), op);
        boolean result = p.parseOr();
        if (!p.atEnd())
            throw new IllegalStateException("trailing tokens " + p.remaining() + " in rule: " + rule);
        return result;
    }

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int n = s.length();
        for (int i = 0; i < n; ) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isLetter(c)) {
                int j = i + 1;
                while (j < n && Character.isLetter(s.charAt(j))) j++;
                out.add(s.substring(i, j));
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && Character.isDigit(s.charAt(j))) j++;
                out.add(s.substring(i, j));
                i = j;
            } else if (c == '.' && i + 1 < n && s.charAt(i + 1) == '.') {
                out.add("..");
                i += 2;
            } else if (c == '!' && i + 1 < n && s.charAt(i + 1) == '=') {
                out.add("!=");
                i += 2;
            } else if (c == '%' || c == '=' || c == ',') {
                out.add(String.valueOf(c));
                i++;
            } else {
                i++;
            }
        }
        return out;
    }

    private static class Parser {
        final List<String> tokens;
        final Operands op;
        int pos;

        Parser(List<String> tokens, Operands op) { this.tokens = tokens; this.op = op; }

        boolean atEnd() { return pos >= tokens.size(); }
        String peek() { return pos < tokens.size() ? tokens.get(pos) : ""; }
        String consume() { return tokens.get(pos++); }
        String remaining() { return tokens.subList(pos, tokens.size()).toString(); }

        boolean parseOr() {
            boolean v = parseAnd();
            while (peek().equals("or")) { consume(); v |= parseAnd(); }
            return v;
        }

        boolean parseAnd() {
            boolean v = parseRelation();
            while (peek().equals("and")) { consume(); v &= parseRelation(); }
            return v;
        }

        boolean parseRelation() {
            double lhs = parseExpr();
            String oper = consume();
            if (oper.equals("is")) {
                boolean negate = false;
                if (peek().equals("not")) { consume(); negate = true; }
                long val = Long.parseLong(consume());
                return negate ^ (lhs == val);
            }
            boolean negate = false, within = false;
            if (oper.equals("not")) {
                String next = consume();
                if (next.equals("in"))           { negate = true; }
                else if (next.equals("within"))  { negate = true; within = true; }
                else throw new IllegalStateException("unexpected after 'not': " + next);
            } else if (oper.equals("in") || oper.equals("=")) {
                // integer-range semantics (default)
            } else if (oper.equals("!=")) {
                negate = true;
            } else if (oper.equals("within")) {
                within = true;
            } else {
                throw new IllegalStateException("unexpected operator: " + oper);
            }
            boolean match = parseRangeList(lhs, within);
            return negate ^ match;
        }

        double parseExpr() {
            String name = consume();
            double v = op.operand(name);
            if (peek().equals("%")) {
                consume();
                long mod = Long.parseLong(consume());
                v = v - mod * Math.floor(v / mod);
            }
            return v;
        }

        boolean parseRangeList(double val, boolean within) {
            boolean any = parseRange(val, within);
            while (peek().equals(",")) { consume(); any |= parseRange(val, within); }
            return any;
        }

        boolean parseRange(double val, boolean within) {
            long lo = Long.parseLong(consume());
            if (peek().equals("..")) {
                consume();
                long hi = Long.parseLong(consume());
                if (within) return val >= lo && val <= hi;
                return val == Math.floor(val) && val >= lo && val <= hi;
            }
            return val == lo;
        }
    }
}
