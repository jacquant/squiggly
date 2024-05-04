package com.github.jacquant.squiggly.config;

import com.github.jacquant.squiggly.bean.BeanInfoIntrospector;
import com.github.jacquant.squiggly.filter.SquigglyPropertyFilter;
import com.github.jacquant.squiggly.parser.SquigglyParser;
import com.github.jacquant.squiggly.view.PropertyView;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import net.jcip.annotations.ThreadSafe;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;

/**
 * Provides access to various configuration values that the Squiggly library uses.
 * <p>
 * Users can override the default configuration by putting a squiggly.properties in their classpath.
 */
@ThreadSafe
public class SquigglyConfig {

    private static final SortedMap<String, String> PROPS_MAP;
    private static final SortedMap<String, String> SOURCE_MAP;

    private static final boolean filterImplicitlyIncludeBaseFields;
    private static final boolean filterImplicitlyIncludeBaseFieldsInView;
    private static final CacheBuilderSpec filterPathCacheSpec;
    private static final boolean filterPropagateViewToNestedFilters;

    private static final CacheBuilderSpec parserNodeCacheSpec;

    private static boolean propertyAddNonAnnotatedFieldsToBaseView;
    private static final CacheBuilderSpec propertyDescriptorCacheSpec;

    static {
        Map<String, String> propsMap = Maps.newHashMap();
        Map<String, String> sourceMap = Maps.newHashMap();

        loadProps(propsMap, sourceMap, "squiggly.default.properties");
        loadProps(propsMap, sourceMap, "squiggly.properties");

        PROPS_MAP = ImmutableSortedMap.copyOf(propsMap);
        SOURCE_MAP = ImmutableSortedMap.copyOf(sourceMap);

        filterImplicitlyIncludeBaseFields = getBool(PROPS_MAP, "filter.implicitlyIncludeBaseFields");
        filterImplicitlyIncludeBaseFieldsInView = getBool(PROPS_MAP, "filter.implicitlyIncludeBaseFieldsInView");
        filterPathCacheSpec = getCacheSpec(PROPS_MAP, "filter.pathCache.spec");
        filterPropagateViewToNestedFilters = getBool(PROPS_MAP, "filter.propagateViewToNestedFilters");
        parserNodeCacheSpec = getCacheSpec(PROPS_MAP, "parser.nodeCache.spec");
        propertyAddNonAnnotatedFieldsToBaseView = getBool(PROPS_MAP, "property.addNonAnnotatedFieldsToBaseView");
        propertyDescriptorCacheSpec = getCacheSpec(PROPS_MAP, "property.descriptorCache.spec");
    }

    private static CacheBuilderSpec getCacheSpec(Map<String, String> props, String key) {
        String value = props.get(key);

        if (value == null) {
            value = "";
        }

        return CacheBuilderSpec.parse(value);
    }

    private static boolean getBool(Map<String, String> props, String key) {
        return "true".equals(props.get(key));
    }

    private static int getInt(Map<String, String> props, String key) {
        try {
            return Integer.parseInt(props.get(key));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Unable to convert " + props.get(key) + " to int for key " + key);
        }
    }

    private static void loadProps(Map<String, String> propsMap, Map<String, String> sourceMap, String file) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource(file);

        if (url == null) {
            return;
        }

        Properties fileProps = new Properties();
        InputStream inputStream = null;


        try {
            inputStream = url.openStream();
            fileProps.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load properties from classpath resource " + file, e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception eat) {
                // ignore
            }
        }

        for (Map.Entry<Object, Object> entry : fileProps.entrySet()) {
            propsMap.put(entry.getKey().toString(), entry.getValue().toString());
            sourceMap.put(entry.getKey().toString(), url.toString());
        }
    }

    private SquigglyConfig() {
    }

    /**
     * Determines whether or not to include base fields for nested objects
     *
     * @return true if includes, false if not
     * @see PropertyView
     */
    public static boolean isFilterImplicitlyIncludeBaseFields() {
        return filterImplicitlyIncludeBaseFields;
    }

    /**
     * Determines whether or not filters that specify a view also include "base" fields.
     *
     * @return true if includes, false if not
     */
    public static boolean isFilterImplicitlyIncludeBaseFieldsInView() {
        return filterImplicitlyIncludeBaseFieldsInView;
    }

    /**
     * Get the {@link CacheBuilderSpec} of the path cache in the squiggly filter.
     *
     * @return spec
     * @see SquigglyPropertyFilter
     */
    public static CacheBuilderSpec getFilterPathCacheSpec() {
        return filterPathCacheSpec;
    }

    /**
     * Determines whether or not filters that specify a view also propagtes that view to nested filters.
     * <p>
     * For example, given a view called "full", does the full view also apply to the nested objects or does the nested
     * object only include base fields.
     *
     * @return true if includes, false if not
     */
    public static boolean isFilterPropagateViewToNestedFilters() {
        return filterPropagateViewToNestedFilters;
    }

    /**
     * Get the {@link CacheBuilderSpec} of the node cache in the squiggly parser.
     *
     * @return spec
     * @see SquigglyParser
     */
    public static CacheBuilderSpec getParserNodeCacheSpec() {
        return parserNodeCacheSpec;
    }

    /**
     * Determines whether or not non-annotated fields are added to the "base" view.
     *
     * @return true/false
     * @see BeanInfoIntrospector
     */
    public static boolean isPropertyAddNonAnnotatedFieldsToBaseView() {
        return propertyAddNonAnnotatedFieldsToBaseView;
    }

    /**
     * Get the {@link CacheBuilderSpec} of the descriptor cache in the property view introspector.
     *
     * @return spec
     * @see BeanInfoIntrospector
     */
    public static CacheBuilderSpec getPropertyDescriptorCacheSpec() {
        return propertyDescriptorCacheSpec;
    }

    /**
     * Gets all the config as a map.
     *
     * @return map
     */
    public static SortedMap<String, String> asMap() {
        return PROPS_MAP;
    }

    /**
     * Gets a map of all the config keys and whose values are the location where that key was read from.
     *
     * @return source map
     */
    public static SortedMap<String, String> asSourceMap() {
        return SOURCE_MAP;
    }

    public static void main(String[] args) {
        System.out.println(SquigglyConfig.asMap());
    }
}
