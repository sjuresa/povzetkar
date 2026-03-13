package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.ui.exception.UiRenderingException;
import com.povzetkar.quarkus.ui.model.MarkdownContent;
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
import jakarta.enterprise.context.ApplicationScoped;
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
@ApplicationScoped
public class SystemInfoResource {

    private static final Logger LOG = LoggerFactory.getLogger(SystemInfoResource.class);

    private static final DataHolder MARKDOWN_OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(TablesExtension.create()))
            .toImmutable();
    private static final Parser MARKDOWN_PARSER = Parser.builder(MARKDOWN_OPTIONS).build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder(MARKDOWN_OPTIONS).build();

    private static final String CHANGELOG_RESOURCE_PATH = "CHANGELOG.md";
    private static final String ROADMAP_RESOURCE_PATH = "docs/overview/roadmap.md";

    private final Template changelog;
    private final Template roadmap;
    private final ViewUtils viewUtils;
    private final jakarta.enterprise.inject.Instance<RoutingContext> contextInstance;
    private final MarkdownContent cachedChangelog;
    private final MarkdownContent cachedRoadmap;

    @Inject
    public SystemInfoResource(@Location("changelog.qute.html") Template changelog,
            @Location("roadmap.qute.html") Template roadmap,
            ViewUtils viewUtils,
            jakarta.enterprise.inject.Instance<RoutingContext> contextInstance) {
        this.changelog = changelog;
        this.roadmap = roadmap;
        this.viewUtils = viewUtils;
        this.contextInstance = contextInstance;
        this.cachedChangelog = loadMarkdownResource(CHANGELOG_RESOURCE_PATH);
        this.cachedRoadmap = loadMarkdownResource(ROADMAP_RESOURCE_PATH);
    }

    private MarkdownContent loadMarkdownResource(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                String errorMsg = "Resource not found in classpath: " + resourcePath;
                LOG.error(errorMsg);
                return MarkdownContent.error(errorMsg);
            }

            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            if (markdown.trim().isEmpty()) {
                String errorMsg = "Resource is empty: " + resourcePath;
                LOG.warn(errorMsg);
                return MarkdownContent.warning(errorMsg);
            }

            return MarkdownContent.success(MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown)));
        } catch (Exception e) {
            String errorMsg = "Error reading or rendering resource " + resourcePath + ": " + e.getMessage();
            LOG.error(errorMsg, e);
            return MarkdownContent.error(errorMsg);
        }
    }

    @GET
    @Path("/changelog")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getChangelog() {
        return renderPage(changelog, cachedChangelog, "Changelog", "/ui/system/changelog");
    }

    @GET
    @Path("/roadmap")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getRoadmap() {
        return renderPage(roadmap, cachedRoadmap, "Roadmap", "/ui/system/roadmap");
    }

    private TemplateInstance renderPage(Template template, MarkdownContent content, String title, String path) {
        try {
            Map<String, Object> data = viewUtils.createTemplateData(contextInstance.get());
            data.put("content", content);
            data.put("breadcrumbs", List.of(
                    ViewUtils.HOME_BREADCRUMB,
                    new Breadcrumb(title, path)));
            return template.data(data);
        } catch (Exception e) {
            throw new UiRenderingException("Failed to render " + title.toLowerCase() + " page", e);
        }
    }
}
