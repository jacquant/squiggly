package com.github.jacquant.squiggly.parser;

import com.github.jacquant.squiggly.config.SquigglyConfig;
import com.github.jacquant.squiggly.metric.source.GuavaCacheSquigglyMetricsSource;
import com.github.jacquant.squiggly.metric.source.SquigglyMetricsSource;
import com.github.jacquant.squiggly.name.*;
import com.github.jacquant.squiggly.parser.antlr4.SquigglyExpressionBaseVisitor;
import com.github.jacquant.squiggly.parser.antlr4.SquigglyExpressionLexer;
import com.github.jacquant.squiggly.parser.antlr4.SquigglyExpressionParser;
import com.github.jacquant.squiggly.util.antlr4.ThrowingErrorListener;
import com.github.jacquant.squiggly.view.PropertyView;
import com.github.jacquant.squiggly.name.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.jcip.annotations.ThreadSafe;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * The parser takes a filter expression and compiles it to an Abstract Syntax Tree (AST).  In this parser's case, the
 * tree doesn't have a root node but rather just returns top level nodes.
 */
@ThreadSafe
public class SquigglyParser {

    // Caches parsed filter expressions
    private static final Cache<String, List<SquigglyNode>> CACHE;
    private static final SquigglyMetricsSource METRICS_SOURCE;

    static {
        CACHE = CacheBuilder.from(SquigglyConfig.getParserNodeCacheSpec()).build();
        METRICS_SOURCE = new GuavaCacheSquigglyMetricsSource("squiggly.parser.nodeCache.", CACHE);
    }

    /**
     * Parse a filter expression.
     *
     * @param filter the filter expression
     * @return compiled nodes
     */
    public List<SquigglyNode> parse(String filter) {
        filter = StringUtils.trim(filter);

        if (StringUtils.isEmpty(filter)) {
            return Collections.emptyList();
        }

        // get it from the cache if we can
        List<SquigglyNode> cachedNodes = CACHE.getIfPresent(filter);

        if (cachedNodes != null) {
            return cachedNodes;
        }


        SquigglyExpressionLexer lexer = ThrowingErrorListener.overwrite(new SquigglyExpressionLexer(new ANTLRInputStream(filter)));
        SquigglyExpressionParser parser = ThrowingErrorListener.overwrite(new SquigglyExpressionParser(new CommonTokenStream(lexer)));

        Visitor visitor = new Visitor();
        List<SquigglyNode> nodes = Collections.unmodifiableList(visitor.visit(parser.parse()));

        CACHE.put(filter, nodes);
        return nodes;
    }

    public static SquigglyMetricsSource getMetricsSource() {
        return METRICS_SOURCE;
    }

    private class Visitor extends SquigglyExpressionBaseVisitor<List<SquigglyNode>> {
        @Override
        public List<SquigglyNode> visitParse(SquigglyExpressionParser.ParseContext ctx) {
            MutableNode root = new MutableNode(new ExactName("root")).dotPathed(true);
            handleExpressionList(ctx.expression_list(), root);
            MutableNode analyzedRoot = analyze(root);
            return analyzedRoot.toSquigglyNode().getChildren();
        }

        private void handleExpressionList(SquigglyExpressionParser.Expression_listContext ctx, MutableNode parent) {
            List<SquigglyExpressionParser.ExpressionContext> expressions = ctx.expression();

            for (SquigglyExpressionParser.ExpressionContext expressionContext : expressions) {
                handleExpression(expressionContext, parent);
            }
        }

        private void handleExpression(SquigglyExpressionParser.ExpressionContext ctx, MutableNode parent) {

            if (ctx.negated_expression() != null) {
                handleNegatedExpression(ctx.negated_expression(), parent);
            }

            List<SquigglyName> names;

            if (ctx.field() != null) {
                names = Collections.singletonList(createName(ctx.field()));
            } else if (ctx.dot_path() != null) {
                parent.squiggly = true;
                for (int i = 0; i < ctx.dot_path().field().size() - 1; i++) {
                    parent = parent.addChild(new MutableNode(createName(ctx.dot_path().field(i))).dotPathed(true));
                    parent.squiggly = true;
                }
                names = Collections.singletonList(createName(ctx.dot_path().field().get(ctx.dot_path().field().size() - 1)));
            } else if (ctx.field_list() != null) {
                names = new ArrayList<>(ctx.field_list().field().size());
                for (SquigglyExpressionParser.FieldContext fieldContext : ctx.field_list().field()) {
                    names.add(createName(fieldContext));
                }
            } else if (ctx.deep() != null) {
                names = Collections.singletonList((SquigglyName) AnyDeepName.get());
            } else {
                names = Collections.emptyList();
            }


            for (SquigglyName name : names) {
                MutableNode node = parent.addChild(new MutableNode(name));

                if (ctx.empty_nested_expression() != null) {
                    node.emptyNested = true;
                } else if (ctx.nested_expression() != null) {
                    node.squiggly = true;
                    handleExpressionList(ctx.nested_expression().expression_list(), node);
                }
            }
        }

        private SquigglyName createName(SquigglyExpressionParser.FieldContext ctx) {
            SquigglyName name;

            if (ctx.exact_field() != null) {
                name = new ExactName(ctx.getText());
            } else if (ctx.wildcard_field() != null) {
                name = new WildcardName(ctx.getText());
            } else if (ctx.regex_field() != null) {
                String regexPattern = ctx.regex_field().regex_pattern().getText();
                Set<String> regexFlags = new HashSet<>(ctx.regex_field().regex_flag().size());

                for (SquigglyExpressionParser.Regex_flagContext regex_flagContext : ctx.regex_field().regex_flag()) {
                    regexFlags.add(regex_flagContext.getText());
                }

                name = new RegexName(regexPattern, regexFlags);
            } else if (ctx.wildcard_shallow_field() != null) {
                name = AnyShallowName.get();
            } else {
                throw new IllegalArgumentException("Unhandled field: " + ctx.getText());
            }

            return name;
        }


        private void handleNegatedExpression(SquigglyExpressionParser.Negated_expressionContext ctx, MutableNode parent) {
            if (ctx.field() != null) {
                parent.addChild(new MutableNode(createName(ctx.field())).negated(true));
            } else if (ctx.dot_path() != null) {
                for (int i = 0; i < ctx.dot_path().field().size(); i++) {
                    SquigglyExpressionParser.FieldContext fieldContext = ctx.dot_path().field(i);
                    parent.squiggly = true;

                    MutableNode mutableNode = new MutableNode(createName(fieldContext));
                    mutableNode.negativeParent = true;

                    parent = parent.addChild(mutableNode.dotPathed(true));
                }

                parent.negated(true);
                parent.negativeParent = false;
            }
        }

    }

    private MutableNode analyze(MutableNode node) {
        Map<MutableNode, MutableNode> nodesToAdd = new IdentityHashMap<>();
        MutableNode analyze = analyze(node, nodesToAdd);

        for (Map.Entry<MutableNode, MutableNode> entry : nodesToAdd.entrySet()) {
            entry.getKey().addChild(entry.getValue());
        }

        return analyze;
    }

    private MutableNode analyze(MutableNode node, Map<MutableNode, MutableNode> nodesToAdd) {
        if (node.children != null && !node.children.isEmpty()) {
            boolean allNegated = true;

            for (MutableNode child : node.children.values()) {
                if (!child.negated && !child.negativeParent) {
                    allNegated = false;
                    break;
                }
            }

            if (allNegated) {
                nodesToAdd.put(node, new MutableNode(newBaseViewName()).dotPathed(node.dotPathed));
            }

            for (MutableNode child : node.children.values()) {
                analyze(child, nodesToAdd);
            }
        }

        return node;
    }

    private class MutableNode {
        public boolean negativeParent;
        private SquigglyName name;
        private boolean negated;
        private boolean squiggly;
        private boolean emptyNested;
        private Map<String, MutableNode> children;
        private boolean dotPathed;
        private MutableNode parent;

        MutableNode(SquigglyName name) {
            this.name = name;
        }

        SquigglyNode toSquigglyNode() {
            if (name == null) {
                throw new IllegalArgumentException("No Names specified");
            }

            List<SquigglyNode> childNodes;

            if (children == null || children.isEmpty()) {
                childNodes = Collections.emptyList();
            } else {
                childNodes = new ArrayList<>(children.size());

                for (MutableNode child : children.values()) {
                    childNodes.add(child.toSquigglyNode());
                }

            }

            return newSquigglyNode(name, childNodes);
        }

        private SquigglyNode newSquigglyNode(SquigglyName name, List<SquigglyNode> childNodes) {
            return new SquigglyNode(name, childNodes, negated, squiggly, emptyNested);
        }

        public MutableNode dotPathed(boolean dotPathed) {
            this.dotPathed = dotPathed;
            return this;
        }

        public MutableNode negated(boolean negated) {
            this.negated = negated;
            return this;
        }

        public MutableNode addChild(MutableNode childToAdd) {
            if (children == null) {
                children = new LinkedHashMap<>();
            }

            String name = childToAdd.name.getName();
            MutableNode existingChild = children.get(name);

            if (existingChild == null) {
                childToAdd.parent = this;
                children.put(name, childToAdd);
            } else {
                if (childToAdd.children != null) {

                    if (existingChild.children == null) {
                        existingChild.children = childToAdd.children;
                    } else {
                        existingChild.children.putAll(childToAdd.children);
                    }
                }


                existingChild.squiggly = existingChild.squiggly || childToAdd.squiggly;
                existingChild.emptyNested = existingChild.emptyNested && childToAdd.emptyNested;
                existingChild.dotPathed = existingChild.dotPathed && childToAdd.dotPathed;
                existingChild.negativeParent = existingChild.negativeParent && childToAdd.negativeParent;
                childToAdd = existingChild;
            }

            if (!childToAdd.dotPathed && dotPathed) {
                dotPathed = false;
            }

            return childToAdd;
        }
    }

    private ExactName newBaseViewName() {
        return new ExactName(PropertyView.BASE_VIEW);
    }
}
