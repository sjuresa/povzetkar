package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.ui.exception.UiRenderingException;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Path("/ui/system")
@RolesAllowed("user")
public class SystemInfoResource {

    private static final Logger LOG = LoggerFactory.getLogger(SystemInfoResource.class);

    private static final DataHolder MARKDOWN_OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(TablesExtension.create()))
            .toImmutable();
    private static final Parser MARKDOWN_PARSER = Parser.builder(MARKDOWN_OPTIONS).build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder(MARKDOWN_OPTIONS).build();

    private static final String ERROR_RESOURCE_NOT_FOUND_TEMPLATE = "<div class='alert alert-danger'><strong>Error:</strong> Resource not found in classpath: %s</div>";
    private static final String WARNING_RESOURCE_EMPTY_TEMPLATE = "<div class='alert alert-warning'><strong>Warning:</strong> Resource is empty: %s</div>";
    private static final String ERROR_PROCESSING_RESOURCE_TEMPLATE = "<div class='alert alert-danger'>Error processing resource <strong>%s</strong>: %s</div>";

    private final Template changelog;
    private final Template roadmap;
    private final ViewUtils viewUtils;
    private final RoutingContext context;
    private final String cachedChangelogHtml;
    private final String cachedRoadmapHtml;

    @Inject
    public SystemInfoResource(@Location("changelog.qute.html") Template changelog,
            @Location("roadmap.qute.html") Template roadmap,
            ViewUtils viewUtils,
            RoutingContext context) {
        this.changelog = changelog;
        this.roadmap = roadmap;
        this.viewUtils = viewUtils;
        this.context = context;
        this.cachedChangelogHtml = renderMarkdownResourceToHtml("changelog.md");
        this.cachedRoadmapHtml = renderMarkdownResourceToHtml("docs/overview/roadmap.md");
    }

    private String renderMarkdownResourceToHtml(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                String errorMsg = "Resource not found in classpath: " + resourcePath;
                LOG.error(errorMsg);
                return String.format(ERROR_RESOURCE_NOT_FOUND_TEMPLATE, resourcePath);
            }

            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

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
        return renderPage(changelog, cachedChangelogHtml, "Changelog", "/ui/system/changelog");
    }

    @GET
    @Path("/roadmap")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getRoadmap() {
        return renderPage(roadmap, cachedRoadmapHtml, "Roadmap", "/ui/system/roadmap");
    }

    private TemplateInstance renderPage(Template template, String markdownHtml, String title, String path) {
        try {
            Map<String, Object> data = viewUtils.createTemplateData(context);
            data.put("renderedMarkdown", markdownHtml);
            data.put("breadcrumbs", List.of(
                    ViewUtils.HOME_BREADCRUMB,
                    new Breadcrumb(title, path)));
            return template.data(data);
        } catch (Exception e) {
            throw new UiRenderingException("Failed to render " + title.toLowerCase() + " page", e);
        }
    }
}
