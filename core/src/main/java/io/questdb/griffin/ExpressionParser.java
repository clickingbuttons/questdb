/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin;

import io.questdb.griffin.model.ExpressionNode;
import io.questdb.std.*;

import java.util.ArrayDeque;
import java.util.Deque;

class ExpressionParser {

    private static final IntHashSet nonLiteralBranches = new IntHashSet();
    private static final int BRANCH_NONE = 0;
    private static final int BRANCH_COMMA = 1;
    private static final int BRANCH_LEFT_BRACE = 2;
    private static final int BRANCH_RIGHT_BRACE = 3;
    private static final int BRANCH_CONSTANT = 4;
    private static final int BRANCH_OPERATOR = 5;
    private static final int BRANCH_LITERAL = 6;
    private static final int BRANCH_LAMBDA = 7;
    private static final int BRANCH_CASE_START = 9;
    private static final int BRANCH_CASE_CONTROL = 10;
    private static final LowerCaseAsciiCharSequenceIntHashMap caseKeywords = new LowerCaseAsciiCharSequenceIntHashMap();

    static {
        nonLiteralBranches.add(BRANCH_RIGHT_BRACE);
        nonLiteralBranches.add(BRANCH_CONSTANT);
        nonLiteralBranches.add(BRANCH_LITERAL);
        nonLiteralBranches.add(BRANCH_LAMBDA);

        caseKeywords.put("when", 0);
        caseKeywords.put("then", 1);
        caseKeywords.put("else", 2);
    }

    private final Deque<ExpressionNode> opStack = new ArrayDeque<>();
    private final IntStack paramCountStack = new IntStack();
    private final ObjectPool<ExpressionNode> sqlNodePool;
    private final SqlParser sqlParser;

    ExpressionParser(ObjectPool<ExpressionNode> sqlNodePool, SqlParser sqlParser) {
        this.sqlNodePool = sqlNodePool;
        this.sqlParser = sqlParser;
    }

    private static SqlException missingArgs(int position) {
        return SqlException.$(position, "missing arguments");
    }

    @SuppressWarnings("ConstantConditions")
    void parseExpr(GenericLexer lexer, ExpressionParserListener listener) throws SqlException {
        try {
            int paramCount = 0;
            int braceCount = 0;
            int caseCount = 0;

            ExpressionNode node;
            CharSequence tok;
            char thisChar;
            int prevBranch;
            int thisBranch = BRANCH_NONE;

            OUT:
            while ((tok = SqlUtil.fetchNext(lexer)) != null) {
                thisChar = tok.charAt(0);
                prevBranch = thisBranch;
                boolean processDefaultBranch = false;
                switch (thisChar) {
                    case ',':
                        if (prevBranch == BRANCH_COMMA || prevBranch == BRANCH_LEFT_BRACE) {
                            throw missingArgs(lexer.lastTokenPosition());
                        }
                        thisBranch = BRANCH_COMMA;

                        if (braceCount == 0) {
                            // comma outside of braces
                            lexer.unparse();
                            break OUT;
                        }

                        // If the token is a function argument separator (e.g., a comma):
                        // Until the token at the top of the stack is a left parenthesis,
                        // pop operators off the stack onto the output queue. If no left
                        // parentheses are encountered, either the separator was misplaced or
                        // parentheses were mismatched.
                        while ((node = opStack.poll()) != null && node.token.charAt(0) != '(') {
                            listener.onNode(node);
                        }

                        if (node != null) {
                            opStack.push(node);
                        }

                        paramCount++;
                        break;

                    case '(':
                        thisBranch = BRANCH_LEFT_BRACE;
                        braceCount++;
                        // If the token is a left parenthesis, then push it onto the stack.
                        paramCountStack.push(paramCount);
                        paramCount = 0;
                        // precedence must be max value to make sure control node isn't
                        // consumed as parameter to a greedy function
                        opStack.push(sqlNodePool.next().of(ExpressionNode.CONTROL, "(", Integer.MAX_VALUE, lexer.lastTokenPosition()));
                        break;

                    case ')':
                        if (prevBranch == BRANCH_COMMA) {
                            throw missingArgs(lexer.lastTokenPosition());
                        }

                        if (braceCount == 0) {
                            lexer.unparse();
                            break OUT;
                        }

                        thisBranch = BRANCH_RIGHT_BRACE;
                        braceCount--;
                        // If the token is a right parenthesis:
                        // Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue.
                        // Pop the left parenthesis from the stack, but not onto the output queue.
                        //        If the token at the top of the stack is a function token, pop it onto the output queue.
                        //        If the stack runs out without finding a left parenthesis, then there are mismatched parentheses.
                        while ((node = opStack.poll()) != null && node.token.charAt(0) != '(') {
                            listener.onNode(node);
                        }

                        // enable operation or literal absorb parameters
                        if ((node = opStack.peek()) != null && (node.type == ExpressionNode.LITERAL || (node.type == ExpressionNode.SET_OPERATION))) {
                            node.paramCount = (prevBranch == BRANCH_LEFT_BRACE ? 0 : paramCount + 1) + (node.paramCount == 2 ? 1 : 0);
                            node.type = ExpressionNode.FUNCTION;
                            listener.onNode(node);
                            opStack.poll();
                        }

                        if (paramCountStack.notEmpty()) {
                            paramCount = paramCountStack.pop();
                        }
                        break;
                    case 's':
                    case 'S':
                        if (Chars.equalsLowerCaseAscii(tok, "select")) {
                            thisBranch = BRANCH_LAMBDA;
                            // It is highly likely this expression parser will be re-entered when
                            // parsing sub-query. To prevent sub-query consuming operation stack we must add a
                            // control node, which would prevent such consumption

                            // precedence must be max value to make sure control node isn't
                            // consumed as parameter to a greedy function
                            opStack.push(sqlNodePool.next().of(ExpressionNode.CONTROL, "|", Integer.MAX_VALUE, lexer.lastTokenPosition()));


                            int pos = lexer.lastTokenPosition();
                            // allow sub-query to parse "select" keyword
                            lexer.unparse();

                            ExpressionNode n = sqlNodePool.next().of(ExpressionNode.QUERY, null, 0, pos);
                            n.queryModel = sqlParser.parseSubQuery(lexer);
                            listener.onNode(n);

                            // pop our control node if sub-query hasn't done it
                            ExpressionNode control = opStack.peek();
                            if (control != null && control.type == ExpressionNode.CONTROL && Chars.equals(control.token, '|')) {
                                opStack.poll();
                            }

                            // re-introduce closing brace to this loop
                            lexer.unparse();
                        } else {
                            processDefaultBranch = true;
                        }
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                    case '"':
                    case '\'':
                        if (nonLiteralBranches.excludes(thisBranch)) {
                            thisBranch = BRANCH_CONSTANT;
                            // If the token is a number, then add it to the output queue.
                            opStack.push(sqlNodePool.next().of(ExpressionNode.CONSTANT, GenericLexer.immutableOf(tok), 0, lexer.lastTokenPosition()));
                            break;
                        } else {
                            if (opStack.size() > 1) {
                                throw SqlException.$(lexer.lastTokenPosition(), "dangling expression");
                            }
                            lexer.unparse();
                            break OUT;
                        }
                    case 'N':
                    case 'n':
                    case 't':
                    case 'T':
                    case 'f':
                    case 'F':
                        if (Chars.equalsLowerCaseAscii(tok, "nan") || Chars.equalsLowerCaseAscii(tok, "null")
                                || Chars.equalsLowerCaseAscii(tok, "true") || Chars.equalsLowerCaseAscii(tok, "false")) {
                            thisBranch = BRANCH_CONSTANT;
                            // If the token is a number, then add it to the output queue.
                            opStack.push(sqlNodePool.next().of(ExpressionNode.CONSTANT, GenericLexer.immutableOf(tok), 0, lexer.lastTokenPosition()));
                            break;
                        }
                    default:
                        processDefaultBranch = true;
                        break;
                }

                if (processDefaultBranch) {
                    OperatorExpression op;
                    if ((op = OperatorExpression.opMap.get(tok)) != null) {

                        thisBranch = BRANCH_OPERATOR;

                        // If the token is an operator, o1, then:
                        // while there is an operator token, o2, at the top of the operator stack, and either
                        // o1 is left-associative and its precedence is less than or equal to that of o2, or
                        // o1 is right associative, and has precedence less than that of o2,
                        //        then pop o2 off the operator stack, onto the output queue;
                        // push o1 onto the operator stack.

                        int operatorType = op.type;


                        if (thisChar == '-') {
                            switch (prevBranch) {
                                case BRANCH_OPERATOR:
                                case BRANCH_LEFT_BRACE:
                                case BRANCH_COMMA:
                                case BRANCH_NONE:
                                    // we have unary minus
                                    operatorType = OperatorExpression.UNARY;
                                    break;
                                default:
                                    break;
                            }
                        }

                        ExpressionNode other;
                        // UNARY operators must never pop BINARY ones regardless of precedence
                        // this is to maintain correctness of -a^b
                        while ((other = opStack.peek()) != null) {
                            boolean greaterPrecedence = (op.leftAssociative && op.precedence >= other.precedence) || (!op.leftAssociative && op.precedence > other.precedence);
                            if (greaterPrecedence &&
                                    (operatorType != OperatorExpression.UNARY || (operatorType == OperatorExpression.UNARY && other.paramCount == 1))) {
                                listener.onNode(other);
                                opStack.poll();
                            } else {
                                break;
                            }
                        }
                        node = sqlNodePool.next().of(
                                op.type == OperatorExpression.SET ? ExpressionNode.SET_OPERATION : ExpressionNode.OPERATION,
                                op.token,
                                op.precedence,
                                lexer.lastTokenPosition()
                        );
                        if (operatorType == OperatorExpression.UNARY) {
                            node.paramCount = 1;
                        } else {
                            node.paramCount = 2;
                        }
                        opStack.push(node);
                    } else if (caseCount > 0 || nonLiteralBranches.excludes(thisBranch)) {

                        // here we handle literals, in case of "case" statement some of these literals
                        // are going to flush operation stack
                        if (Chars.toLowerCaseAscii(thisChar) == 'c' && Chars.equalsLowerCaseAscii(tok, "case")) {
                            caseCount++;
                            paramCountStack.push(paramCount);
                            paramCount = 0;
                            opStack.push(sqlNodePool.next().of(ExpressionNode.FUNCTION, GenericLexer.immutableOf(tok), Integer.MAX_VALUE, lexer.lastTokenPosition()));
                            thisBranch = BRANCH_CASE_START;
                            continue;
                        }

                        thisBranch = BRANCH_LITERAL;

                        if (caseCount > 0) {
                            switch (Chars.toLowerCaseAscii(thisChar)) {
                                case 'e':
                                    if (Chars.equalsLowerCaseAscii(tok, "end")) {
                                        if (prevBranch == BRANCH_CASE_CONTROL) {
                                            throw missingArgs(lexer.lastTokenPosition());
                                        }

                                        if (paramCount == 0) {
                                            throw SqlException.$(lexer.lastTokenPosition(), "'when' expected");
                                        }

                                        // If the token is a right parenthesis:
                                        // Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue.
                                        // Pop the left parenthesis from the stack, but not onto the output queue.
                                        //        If the token at the top of the stack is a function token, pop it onto the output queue.
                                        //        If the stack runs out without finding a left parenthesis, then there are mismatched parentheses.
                                        while ((node = opStack.poll()) != null && !Chars.equalsLowerCaseAscii(node.token, "case")) {
                                            listener.onNode(node);
                                        }

                                        node.paramCount = paramCount;
                                        listener.onNode(node);

                                        // make sure we restore paramCount
                                        if (paramCountStack.notEmpty()) {
                                            paramCount = paramCountStack.pop();
                                        }

                                        caseCount--;
                                        continue;
                                    }
                                    // fall through
                                case 'w':
                                case 't':
                                    int keywordIndex = caseKeywords.get(tok);
                                    if (keywordIndex > -1) {

                                        if (prevBranch == BRANCH_CASE_CONTROL) {
                                            throw missingArgs(lexer.lastTokenPosition());
                                        }

                                        int argCount = 0;
                                        while ((node = opStack.poll()) != null && !Chars.equalsLowerCaseAscii(node.token, "case")) {
                                            listener.onNode(node);
                                            argCount++;
                                        }


                                        if (paramCount == 0) {
                                            if (argCount == 0) {
                                                // this is 'case when', we will
                                                // indicate that this is regular 'case' to the rewrite logic
                                                listener.onNode(sqlNodePool.next().of(ExpressionNode.LITERAL, null, Integer.MIN_VALUE, -1));
                                            }
                                            paramCount++;
                                        }

                                        switch (keywordIndex) {
                                            case 0: // when
                                            case 2: // else
                                                if ((paramCount % 2) == 0) {
                                                    throw SqlException.$(lexer.lastTokenPosition(), "'then' expected");
                                                }
                                                break;
                                            default: // then
                                                if ((paramCount % 2) != 0) {
                                                    throw SqlException.$(lexer.lastTokenPosition(), "'when' expected");
                                                }
                                                break;
                                        }

                                        if (node != null) {
                                            opStack.push(node);
                                        }

                                        paramCount++;
                                        thisBranch = BRANCH_CASE_CONTROL;
                                        continue;
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }

                        // If the token is a function token, then push it onto the stack.
                        opStack.push(sqlNodePool.next().of(ExpressionNode.LITERAL, GenericLexer.immutableOf(tok), Integer.MIN_VALUE, lexer.lastTokenPosition()));
                    } else {
                        // literal can be at start of input, after a bracket or part of an operator
                        // all other cases are illegal and will be considered end-of-input
                        lexer.unparse();
                        break;
                    }
                }
            }

            while ((node = opStack.poll()) != null) {

                if (node.token.charAt(0) == '(') {
                    throw SqlException.$(node.position, "unbalanced (");
                }

                if (Chars.equalsLowerCaseAscii(node.token, "case")) {
                    throw SqlException.$(node.position, "unbalanced 'case'");
                }

                if (node.type == ExpressionNode.CONTROL) {
                    // break on any other control node to allow parser to be reenterable
                    // put control node back on stack because we don't own it
                    opStack.push(node);
                    break;
                }

                listener.onNode(node);
            }

        } catch (SqlException e) {
            paramCountStack.clear();
            opStack.clear();
            throw e;
        }
    }
}
