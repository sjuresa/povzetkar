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
    private static final String CLOSING_DIV = "</div>";

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
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));

        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                String errorMsg = "Resource not found in classpath: " + resourcePath;
                LOG.error(errorMsg);
                return "<div class='alert alert-danger'><strong>Error:</strong> " + errorMsg + CLOSING_DIV;
            }

            String markdown = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            if (markdown.trim().isEmpty()) {
                String errorMsg = "Resource is empty: " + resourcePath;
                LOG.warn(errorMsg);
                return "<div class='alert alert-warning'><strong>Warning:</strong> " + errorMsg + CLOSING_DIV;
            }

            return renderer.render(parser.parse(markdown));
        } catch (Exception e) {
            LOG.error("Error reading or rendering resource {}: {}", resourcePath, e.getMessage(), e);
            return "<div class='alert alert-danger'>Error processing resource <strong>" + resourcePath + "</strong>: "
                    + e.getMessage() + CLOSING_DIV;
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
