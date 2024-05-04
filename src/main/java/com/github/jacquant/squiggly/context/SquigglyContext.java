package com.github.jacquant.squiggly.context;

import com.github.jacquant.squiggly.parser.SquigglyNode;
import com.github.jacquant.squiggly.filter.SquigglyPropertyFilter;

import java.util.List;

/**
 * A squiggly context provides parsing and filtering information to the
 * {@link SquigglyPropertyFilter}.  Contexts are usually not thread safe.
 */
public interface SquigglyContext {

    /**
     * Get the top-level bean class being filtered.
     *
     * @return bean class
     */
    Class getBeanClass();

    /**
     * Get the parsed nodes.
     *
     * @return nodes
     */
    List<SquigglyNode> getNodes();

    /**
     * Get the filter expression.
     *
     * @return filter expression
     */
    String getFilter();
}
