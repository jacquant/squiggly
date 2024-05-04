package com.github.jacquant.squiggly.examples.dropwizard;

import com.github.jacquant.squiggly.Squiggly;
import com.github.jacquant.squiggly.examples.dropwizard.web.IssueResource;
import com.github.jacquant.squiggly.examples.dropwizard.web.ListResponse;
import com.github.jacquant.squiggly.web.RequestSquigglyContextProvider;
import com.github.jacquant.squiggly.web.SquigglyRequestFilter;
import io.dropwizard.Application;
import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumSet;

public class IssueApplication extends Application<IssueConfiguration> {
    public static void main(String[] args) throws Exception {
        new IssueApplication().run("server", "issue-application.yml");
    }

    @Override
    public String getName() {
        return "issue-application";
    }

    @Override
    public void initialize(Bootstrap<IssueConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
                new ResourceConfigurationSourceProvider());
    }

    @Override
    public void run(IssueConfiguration configuration,
                    Environment environment) {
        environment.servlets()
                .addFilter("Squiggly-Filter", new SquigglyRequestFilter())
                .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

        Squiggly.init(environment.getObjectMapper(), new RequestSquigglyContextProvider() {
            @Override
            protected String customizeFilter(String filter, HttpServletRequest request, Class beanClass) {

                // OPTIONAL: automatically wrap filter expressions in items{} when the object is a ListResponse
                if (filter != null && ListResponse.class.isAssignableFrom(beanClass)) {
                    filter = "items[" + filter + "]";
                }

                return filter;
            }
        });

        environment.jersey().register(new IssueResource());

    }

}