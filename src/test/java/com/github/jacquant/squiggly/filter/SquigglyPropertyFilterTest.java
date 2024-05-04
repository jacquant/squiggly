package com.github.jacquant.squiggly.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.jacquant.squiggly.model.*;
import com.github.jacquant.squiggly.config.SquigglyConfig;
import com.github.jacquant.squiggly.context.provider.SimpleSquigglyContextProvider;
import com.github.jacquant.squiggly.parser.SquigglyParser;
import com.github.jacquant.squiggly.util.SquigglyUtils;
import com.google.common.base.Charsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;


@SuppressWarnings("Duplicates")
class SquigglyPropertyFilterTest {

    public static final String BASE_PATH = "com/github/jacquant/squiggly/SquigglyPropertyFilterTest";
    private Issue issue;
    private ObjectMapper objectMapper;
    private SimpleFilterProvider filterProvider;
    private boolean init = false;
    private ObjectMapper rawObjectMapper = new ObjectMapper();

    public SquigglyPropertyFilterTest() {
        rawObjectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @BeforeEach
    void beforeEachTest() {
        if (!init) {
            issue = buildIssue();
            objectMapper = new ObjectMapper();
            filterProvider = new SimpleFilterProvider();
            objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            objectMapper.setFilterProvider(filterProvider);
            objectMapper.addMixIn(Object.class, SquigglyPropertyFilterMixin.class);
            init = true;
        }

        filterProvider.removeFilter(SquigglyPropertyFilter.FILTER_ID);
    }

    private Issue buildIssue() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("email", "motherofdragons@got.com");
        properties.put("priority", "1");

        Issue issue = new Issue();
        issue.setId("ISSUE-1");
        issue.setIssueSummary("Dragons Need Fed");
        issue.setIssueDetails("I need my dragons fed pronto.");
        User assignee = new User("Jorah", "Mormont");
        issue.setAssignee(assignee);
        issue.setReporter(new User("Daenerys", "Targaryen"));
        issue.setActions(Arrays.asList(
                new IssueAction("COMMENT", "I'm going to let Daario get this one..", assignee),
                new IssueAction("CLOSE", "All set.", new User("Daario", "Naharis"))
        ));
        issue.setProperties(properties);
        return issue;
    }


    @Test
    void testAnyDeep() {
        filter("**");
        assertEquals(stringifyRaw(), stringify());
    }

    @Test
    void testAnyShallow() {
        filter("*");
        String raw = regexRemove(stringifyRaw(), ",\"userId\":\"[a-zA-Z0-9-_]+\"");
        raw = regexRemove(raw, ",\"user\":\\{.*?\\}");
        raw = regexRemove(raw, ",\"entityType\":\"User\"");
        assertEquals(raw, stringify());
    }

    @Test
    void testBaseView() {
        filter("base");
        String raw = regexRemove(stringifyRaw(), ",\"actions\":.*") + "}";
        raw = regexRemove(raw, ",\"entityType\":\"User\"");
        assertEquals(raw, stringify());
    }

    @Test
    void testFullView() {
        filter("full");
        String raw = regexRemove(stringifyRaw(), ",\"userId\":\"[a-zA-Z0-9-_]+\"");
        raw = regexRemove(raw, ",\"user\":\\{.*?\\}");
        raw = regexRemove(raw, ",\"entityType\":\"User\"");

        assertEquals(raw, stringify());
    }

    @Test
    void testEmpty() {
        filter("");
        assertEquals("{}", stringify());
    }

    @Test
    void testSingleField() {
        filter("id");
        assertEquals("{\"id\":\"" + issue.getId() + "\"}", stringify());
    }

    @Test
    void testMultipleFields() {
        filter("id,issueSummary");
        assertEquals("{\"id\":\"" + issue.getId() + "\",\"issueSummary\":\"" + issue.getIssueSummary() + "\"}", stringify());
    }

    @Test
    void testRegex() {
        filter("~iss[a-z]e.*~");
        assertEquals("{\"issueSummary\":\"" + issue.getIssueSummary() + "\",\"issueDetails\":\"" + issue.getIssueDetails() + "\"}", stringify());
    }

    @Test
    void testRegexCaseInsensitive() {
        filter("~iss[a-z]esumm.*~i");
        assertEquals("{\"issueSummary\":\"" + issue.getIssueSummary() + "\"}", stringify());
    }

    @Test
    void testRegexTraditional() {
        filter("/iss[a-z]e.*/");
        assertEquals("{\"issueSummary\":\"" + issue.getIssueSummary() + "\",\"issueDetails\":\"" + issue.getIssueDetails() + "\"}", stringify());
    }

    @Test
    void testWildCardSingle() {
        filter("issueSummar?");
        assertEquals("{\"issueSummary\":\"" + issue.getIssueSummary() + "\"}", stringify());
    }

    @Test
    void testWildCardStart() {
        filter("issue*");
        assertEquals("{\"issueSummary\":\"" + issue.getIssueSummary() + "\",\"issueDetails\":\"" + issue.getIssueDetails() + "\"}", stringify());
    }

    @Test
    void testWildCardEnd() {
        filter("*d");
        assertEquals("{\"id\":\"" + issue.getId() + "\"}", stringify());
    }

    @Test
    void testWildCardMiddle() {
        filter("*ue*");
        assertEquals("{\"issueSummary\":\"" + issue.getIssueSummary() + "\",\"issueDetails\":\"" + issue.getIssueDetails() + "\"}", stringify());
    }


    @Test
    void testDotPath() {
        filter("id,actions.user.firstName");
        assertEquals("{\"id\":\"ISSUE-1\",\"actions\":[{\"user\":{\"firstName\":\"Jorah\"}},{\"user\":{\"firstName\":\"Daario\"}}]}", stringify());
    }

    @Test
    void testNegativeDotPath() {
        filter("id,-actions.user.firstName");
        assertEquals("{\"id\":\"ISSUE-1\",\"actions\":[{\"id\":null,\"type\":\"COMMENT\",\"text\":\"I'm going to let Daario get this one..\",\"user\":{\"lastName\":\"Mormont\"}},{\"id\":null,\"type\":\"CLOSE\",\"text\":\"All set.\",\"user\":{\"lastName\":\"Naharis\"}}]}", stringify());
    }

    @Test
    void testNegativeDotPaths() {
        filter("-actions.user.firstName,-actions.user.lastName");
        assertEquals("{\"id\":\"ISSUE-1\",\"issueSummary\":\"Dragons Need Fed\",\"issueDetails\":\"I need my dragons fed pronto.\",\"reporter\":{\"firstName\":\"Daenerys\",\"lastName\":\"Targaryen\"},\"assignee\":{\"firstName\":\"Jorah\",\"lastName\":\"Mormont\"},\"actions\":[{\"id\":null,\"type\":\"COMMENT\",\"text\":\"I'm going to let Daario get this one..\",\"user\":{}},{\"id\":null,\"type\":\"CLOSE\",\"text\":\"All set.\",\"user\":{}}]}", stringify());
    }

    @Test
    void testNestedDotPath() {
        filter("id,actions.user[firstName],issueSummary");
        assertEquals("{\"id\":\"ISSUE-1\",\"issueSummary\":\"Dragons Need Fed\",\"actions\":[{\"user\":{\"firstName\":\"Jorah\"}},{\"user\":{\"firstName\":\"Daario\"}}]}", stringify());

        filter("id,actions.user[]");
        assertEquals("{\"id\":\"ISSUE-1\",\"actions\":[{\"user\":{}},{\"user\":{}}]}", stringify());
    }

    @Test
    void testDeepNestedDotPath() {
        filter("id,items.items[items.id]");
        assertEquals("{\"id\":\"ITEM-1\",\"items\":[{\"items\":[{\"items\":[{\"id\":\"ITEM-4\"}]}]}]}", stringify(Item.testItem()));

        filter("id,items.items[items.items[id]]");
        assertEquals("{\"id\":\"ITEM-1\",\"items\":[{\"items\":[{\"items\":[{\"items\":[{\"id\":\"ITEM-5\"}]}]}]}]}", stringify(Item.testItem()));

        filter("id,items.items[-items.id]");
        assertEquals("{\"id\":\"ITEM-1\",\"items\":[{\"items\":[{\"id\":\"ITEM-3\",\"name\":\"Milkshake\",\"items\":[{\"name\":\"Hoverboard\",\"items\":[{\"id\":\"ITEM-5\",\"name\":\"Binoculars\",\"items\":[]}]}]}]}]}", stringify(Item.testItem()));

        filter("id,items.items[items[-id,-name],id]");
        assertEquals("{\"id\":\"ITEM-1\",\"items\":[{\"items\":[{\"id\":\"ITEM-3\",\"items\":[{\"items\":[{\"id\":\"ITEM-5\",\"name\":\"Binoculars\",\"items\":[]}]}]}]}]}", stringify(Item.testItem()));

        fileTest("company-list.json", "deep-nested-01-filter.txt", "deep-nested-01-expected.json");
        fileTest("task-list.json", "deep-nested-02-filter.txt", "deep-nested-02-expected.json");
        fileTest("task-list.json", "deep-nested-03-filter.txt", "deep-nested-03-expected.json");
    }

    @Test
    void testOtherView() {
        filter("other");
        String raw = regexRemove(stringifyRaw(), ",\"user\":\\{.*?\\}");
        raw = regexRemove(raw, ",\"properties\":\\{.*?\\}");
        raw = regexRemove(raw, ",\"entityType\":\"User\"");
        assertEquals(raw, stringify());
    }

    @Test
    void testNestedEmpty() {
        filter("assignee[]");
        assertEquals("{\"assignee\":{}}", stringify());
    }

    @Test
    void testAssignee() {
        filter("assignee");
        assertEquals("{\"assignee\":{\"firstName\":\"Jorah\",\"lastName\":\"Mormont\"}}", stringify());
    }

    @Test
    void testNestedSingle() {
        filter("assignee[firstName]");
        assertEquals("{\"assignee\":{\"firstName\":\"" + issue.getAssignee().getFirstName() + "\"}}", stringify());
    }

    @Test
    void testNestedMultiple() {
        filter("actions[type,text]");
        assertEquals("{\"actions\":[{\"type\":\"" + issue.getActions().get(0).getType() + "\",\"text\":\"" + issue.getActions().get(0).getText() + "\"},{\"type\":\"" + issue.getActions().get(1).getType() + "\",\"text\":\"" + issue.getActions().get(1).getText() + "\"}]}", stringify());
    }

    @Test
    void testMultipleNestedSingle() {
        filter("(reporter,assignee)[lastName]");
        assertEquals("{\"reporter\":{\"lastName\":\"" + issue.getReporter().getLastName() + "\"},\"assignee\":{\"lastName\":\"" + issue.getAssignee().getLastName() + "\"}}", stringify());
    }

    @Test
    void testNestedMap() {
        filter("properties[priority]");
        assertEquals("{\"properties\":{\"priority\":\"" + issue.getProperties().get("priority") + "\"}}", stringify());
    }

    @Test
    void testDeepNested() {
        filter("actions[user[lastName]]");
        assertEquals("{\"actions\":[{\"user\":{\"lastName\":\"" + issue.getActions().get(0).getUser().getLastName() + "\"}},{\"user\":{\"lastName\":\"" + issue.getActions().get(1).getUser().getLastName() + "\"}}]}", stringify());
    }

    @Test
    void testSameParent() {
        filter("assignee[firstName],assignee[lastName]");
        assertEquals("{\"assignee\":{\"firstName\":\"Jorah\",\"lastName\":\"Mormont\"}}", stringify());

        filter("assignee.firstName,assignee.lastName");
        assertEquals("{\"assignee\":{\"firstName\":\"Jorah\",\"lastName\":\"Mormont\"}}", stringify());

        filter("actions.user[firstName],actions.user[lastName]");
        assertEquals("{\"actions\":[{\"user\":{\"firstName\":\"Jorah\",\"lastName\":\"Mormont\"}},{\"user\":{\"firstName\":\"Daario\",\"lastName\":\"Naharis\"}}]}", stringify());
    }

    @Deprecated
    @Disabled("Modify final attribute is not authorized since JDK 12")
    @Test
    void testFilterExcludesBaseFieldsInView() {
        String fieldName = "filterImplicitlyIncludeBaseFieldsInView";

        try {
            setFieldValue(SquigglyConfig.class, fieldName, false);
            filter("view1");
            assertEquals("{\"properties\":" + stringifyRaw(issue.getProperties()) + "}", stringify());
        } finally {
            setFieldValue(SquigglyConfig.class, fieldName, true);
        }
    }

    @Deprecated
    @Disabled("Modify final attribute is not authorized since JDK 12")
    @Test
    void testPropagateViewToNestedFilters() {
        String fieldName = "filterPropagateViewToNestedFilters";

        try {
            setFieldValue(SquigglyConfig.class, fieldName, true);
            filter("full");
            assertEquals(stringifyRaw(), stringify());
        } finally {
            setFieldValue(SquigglyConfig.class, fieldName, false);
        }
    }

    @Test
    @Deprecated
    @Disabled("Modify final attribute is not authorized since JDK 12")
    void testPropertyAddNonAnnotatedFieldsToBaseView() {
        String fieldName = "propertyAddNonAnnotatedFieldsToBaseView";

        try {
            setFieldValue(SquigglyConfig.class, fieldName, false);
            filter("base");
            assertEquals("{}", stringify());
        } finally {
            setFieldValue(SquigglyConfig.class, fieldName, true);
        }
    }

    @Test
    void testFilterSpecificty() {
        filter("**,reporter[lastName,entityType]");
        String raw = stringifyRaw();
        assertEquals(raw.replace("\"firstName\":\"" + issue.getReporter().getFirstName() + "\",", ""), stringify());

        filter("**,repo*[lastName,entityType],repo*[firstName,entityType]");
        assertEquals(raw, stringify());

        filter("**,reporter[lastName,entityType],repo*[firstName,entityType]");
        assertEquals(raw.replace("\"firstName\":\"" + issue.getReporter().getFirstName() + "\",", ""), stringify());

        filter("**,repo*[firstName,entityType],rep*[lastName,entityType]");
        assertEquals(raw.replace(",\"lastName\":\"" + issue.getReporter().getLastName() + "\"", ""), stringify());

        filter("**,reporter[firstName,entityType],reporter[lastName,entityType]");
        assertEquals(raw, stringify());
    }

    @Test
    void testFilterExclusion() {
        filter("**,reporter[-firstName]");
        assertEquals("{\"id\":\"ISSUE-1\",\"issueSummary\":\"Dragons Need Fed\",\"issueDetails\":\"I need my dragons fed pronto.\",\"reporter\":{\"lastName\":\"Targaryen\"},\"assignee\":{\"firstName\":\"Jorah\",\"lastName\":\"Mormont\",\"entityType\":\"User\"},\"actions\":[{\"id\":null,\"type\":\"COMMENT\",\"text\":\"I'm going to let Daario get this one..\",\"user\":{\"firstName\":\"Jorah\",\"lastName\":\"Mormont\",\"entityType\":\"User\"}},{\"id\":null,\"type\":\"CLOSE\",\"text\":\"All set.\",\"user\":{\"firstName\":\"Daario\",\"lastName\":\"Naharis\",\"entityType\":\"User\"}}],\"properties\":{\"email\":\"motherofdragons@got.com\",\"priority\":\"1\"}}", stringify());
    }

    @Test
    void testJsonUnwrapped() {
        filter("innerText");
        assertEquals("{\"innerText\":\"innerValue\"}", stringify(new Outer("outerValue", "innerValue")));
    }

    @Test
    void testPropertyWithDash() {
        filter("full-name");
        assertEquals("{\"full-name\":\"Fred Flintstone\"}", stringify(new DashObject("ID-1", "Fred Flintstone")));
    }

    private void setFieldValue(Class<?> ownerClass, String fieldName, boolean value) {
        Field field = getField(ownerClass, fieldName);
        try {
            field.setBoolean(null, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeFinalModifier(Field field) {
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Field getField(Class<?> ownerClass, String fieldName) {
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            removeFinalModifier(field);
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private String regexRemove(String input, String regex) {
        Matcher matcher = regex(input, regex);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(sb, "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Matcher regex(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(input);
    }

    @SuppressWarnings("UnusedReturnValue")
    private String filter(String filter) {
        SquigglyParser parser = new SquigglyParser();
        SimpleSquigglyContextProvider provider = new SimpleSquigglyContextProvider(parser, filter);
        filterProvider.addFilter(SquigglyPropertyFilter.FILTER_ID, new SquigglyPropertyFilter(provider));
        return filter;
    }

    private String stringify() {
        return stringify(issue);
    }

    private String stringify(Object object) {
        return SquigglyUtils.stringify(objectMapper, object);
    }


    private String stringifyRaw() {
        return stringifyRaw(issue);
    }

    private String stringifyRaw(Object object) {
        return SquigglyUtils.stringify(rawObjectMapper, object);
    }

    private void fileTest(String inputFile, String filterFile, String expectedFile) {
        String input = readFile(BASE_PATH + "/input/" + inputFile);
        String filter = readFile(BASE_PATH + "/tests/" + filterFile);
        String expected = readFile(BASE_PATH + "/tests/" + expectedFile);

        try {
            Object inputObject = rawObjectMapper.readValue(input, Object.class);
            Object expectedObject = rawObjectMapper.readValue(expected, Object.class);

            filter(sanitizeFilter(filter));
            assertEquals(stringifyRaw(expectedObject), stringify(inputObject));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readFile(String path) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(path);

        if (resource == null) {
            throw new IllegalArgumentException("path " + path + " does not exist");
        }

        try {
            return new String(Files.readAllBytes(Paths.get(resource.toURI())), Charsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String sanitizeFilter(String filter) {
        String[] lines = filter.split("\n");
        StringBuilder builder = new StringBuilder(filter.length());

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("#")) {
                continue;
            }

            builder.append(line.replaceAll("\\s", ""));
        }

        return builder.toString();
    }

    private static class DashObject {

        private String id;

        @JsonProperty("full-name")
        private String fullName;

        public DashObject() {
        }

        public DashObject(String id, String fullName) {
            this.id = id;
            this.fullName = fullName;
        }

        public String getId() {
            return id;
        }

        public String getFullName() {
            return fullName;
        }
    }
}