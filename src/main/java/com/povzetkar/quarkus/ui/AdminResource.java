package com.povzetkar.quarkus.ui;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/ui/admin")
public class AdminResource {

    private final Template admin;

    public AdminResource(@Location("admin.html") Template admin) {
        this.admin = admin;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getAdminPage() {
        return admin.instance();
    }
}
