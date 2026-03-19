package com.povzetkar.quarkus.ui.util;

import com.povzetkar.quarkus.config.AppDetails;
import com.povzetkar.quarkus.ui.TopicResource;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ViewUtils {

    private static final String SUCCESS_MESSAGE_KEY = "successMessage";
    private static final String ERROR_MESSAGE_KEY = "errorMessage";
    private static final String LOCATION_HEADER = "Location";

    public static final Breadcrumb HOME_BREADCRUMB = new Breadcrumb("Povzetkar", TopicResource.UI_BASE_PATH);

    private final AppDetails appDetails;

    @Inject
    public ViewUtils(AppDetails appDetails) {
        this.appDetails = appDetails;
    }

    public void setSuccessMessage(RoutingContext context, String message) {
        context.session().put(SUCCESS_MESSAGE_KEY, message);
    }

    public void setErrorMessage(RoutingContext context, String message) {
        context.session().put(ERROR_MESSAGE_KEY, message);
    }

    public void handleRedirect(RoutingContext context, String path) {
        context.response()
                .putHeader(LOCATION_HEADER, path)
                .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .putHeader("Pragma", "no-cache") // For HTTP 1.0 compatibility
                .setStatusCode(303)
                .end();
    }

    public Map<String, Object> createTemplateData(RoutingContext context) {
        Map<String, Object> data = new HashMap<>();

        data.put(SUCCESS_MESSAGE_KEY, context.session().remove(SUCCESS_MESSAGE_KEY));
        data.put(ERROR_MESSAGE_KEY, context.session().remove(ERROR_MESSAGE_KEY));

        data.put("version", appDetails.getVersion());
        data.put("thisYear", java.time.Year.now().getValue());
        return data;
    }
}
