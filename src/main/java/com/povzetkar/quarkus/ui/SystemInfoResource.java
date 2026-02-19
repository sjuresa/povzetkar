package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.ui.exception.UiRenderingException;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/ui/system")
public class SystemInfoResource {

    private static final Logger LOG = LoggerFactory.getLogger(SystemInfoResource.class);

    private static final MutableDataSet MARKDOWN_OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
    private static final Parser MARKDOWN_PARSER = Parser.builder(MARKDOWN_OPTIONS).build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder(MARKDOWN_OPTIONS).build();

    private static final String ERROR_RESOURCE_NOT_FOUND_TEMPLATE = "<div class='alert alert-danger'><strong>Error:</strong> Resource not found in classpath: %s</div>";
    private static final String WARNING_RESOURCE_EMPTY_TEMPLATE = "<div class='alert alert-warning'><strong>Warning:</strong> Resource is empty: %s</div>";
    private static final String ERROR_PROCESSING_RESOURCE_TEMPLATE = "<div class='alert alert-danger'>Error processing resource <strong>%s</strong>: %s</div>";

    private final Template changelog;
    private final Template roadmap;
    private final ViewUtils viewUtils;
    private final RoutingContext context;

    @Inject
    public SystemInfoResource(@Location("changelog.qute.html") Template changelog,
            @Location("roadmap.qute.html") Template roadmap,
            ViewUtils viewUtils,
            RoutingContext context) {
        this.changelog = changelog;
        this.roadmap = roadmap;
        this.viewUtils = viewUtils;
        this.context = context;
    }

    private String renderMarkdownResourceToHtml(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                String errorMsg = "Resource not found in classpath: " + resourcePath;
                LOG.error(errorMsg);
                return String.format(ERROR_RESOURCE_NOT_FOUND_TEMPLATE, resourcePath);
            }

            String markdown = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            if (markdown.trim().isEmpty()) {
                String errorMsg = "Resource is empty: " + resourcePath;
                LOG.warn(errorMsg);
                return String.format(WARNING_RESOURCE_EMPTY_TEMPLATE, resourcePath);
            }

            return MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown));
        } catch (Exception e) {
            LOG.error("Error reading or rendering resource {}: {}", resourcePath, e.getMessage(), e);
            return String.format(ERROR_PROCESSING_RESOURCE_TEMPLATE, resourcePath, e.getMessage());
        }
    }

    @GET
    @Path("/changelog")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getChangelog() {
        try {
            // changelog.md is at the root of the classpath
            String changelogHtml = renderMarkdownResourceToHtml("changelog.md");
            Map<String, Object> data = viewUtils.createTemplateData(context);
            data.put("renderedMarkdown", changelogHtml);
            data.put("breadcrumbs", List.of(
                    ViewUtils.HOME_BREADCRUMB,
                    new Breadcrumb("Changelog", "/ui/system/changelog")));
            return changelog.data(data);
        } catch (Exception e) {
            throw new UiRenderingException("Failed to render changelog page", e);
        }
    }

    @GET
    @Path("/roadmap")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getRoadmap() {
        try {
            // roadmap.md is in docs/overview in the classpath
            String roadmapHtml = renderMarkdownResourceToHtml("docs/overview/roadmap.md");
            Map<String, Object> data = viewUtils.createTemplateData(context);
            data.put("renderedMarkdown", roadmapHtml);
            data.put("breadcrumbs", List.of(
                    ViewUtils.HOME_BREADCRUMB,
                    new Breadcrumb("Roadmap", "/ui/system/roadmap")));
            return roadmap.data(data);
        } catch (Exception e) {
            throw new UiRenderingException("Failed to render roadmap page", e);
        }
    }
}
