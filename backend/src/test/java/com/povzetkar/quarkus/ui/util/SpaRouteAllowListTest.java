package com.povzetkar.quarkus.ui.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.povzetkar.quarkus.test.util.RepositoryFiles.findInRepository;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the SPA fallback allow-list against the frontend router (POV-221): a route that is reachable as a
 * top-level URL in {@code frontend/src/router.tsx} but whose first segment is not in
 * {@link SpaRoutingHandler#SPA_ROUTE_PREFIXES} would answer 404 on refresh or as a deep link.
 * <p>
 * Each {@code createRoute(...)} is read with its parent and its literal {@code path} or {@code id}. A route is
 * top-level when every ancestor up to {@code rootRoute} is pathless (a layout with an {@code id}, such as the
 * protected layout) — then a relative path like {@code 'settings'} is a top-level URL too.
 */
class SpaRouteAllowListTest {

    private static final String ROOT_ROUTE = "rootRoute";
    private static final Pattern ROUTE = Pattern.compile("const\\s+(\\w+)\\s*=\\s*createRoute\\(\\{");
    private static final Pattern PARENT = Pattern.compile("getParentRoute:\\s*\\(\\)\\s*=>\\s*(\\w+)");
    private static final Pattern PATH = Pattern.compile("\\bpath:\\s*(['\"`])([^'\"`]*)\\1");
    private static final Pattern ID = Pattern.compile("\\bid:\\s*(['\"`])([^'\"`]*)\\1");

    private record RouteDef(String name, String parent, String path) {
        boolean pathless() {
            return path == null;
        }
    }

    private static final Pattern ANY_CREATE_ROUTE_CALL = Pattern.compile("\\bcreateRoute\\s*[<(]");

    private static final Map<String, RouteDef> routes = new HashMap<>();
    private static final List<String> routesWithoutLiteralPathOrId = new ArrayList<>();
    private static int createRouteCalls;

    @BeforeAll
    static void readRouter() throws IOException {
        String router = Files.readString(findInRepository("frontend/src/router.tsx"), StandardCharsets.UTF_8);
        createRouteCalls = (int) ANY_CREATE_ROUTE_CALL.matcher(router).results().count();
        Matcher route = ROUTE.matcher(router);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (route.find()) {
            starts.add(route.end());
            names.add(route.group(1));
        }
        for (int i = 0; i < starts.size(); i++) {
            // The options object of one route: up to the next createRoute (or the end of the file)
            String block = router.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : router.length());
            Matcher parent = PARENT.matcher(block);
            Matcher path = PATH.matcher(block);
            Matcher id = ID.matcher(block);
            String name = names.get(i);
            boolean hasPath = path.find();
            if (!hasPath && !id.find()) {
                routesWithoutLiteralPathOrId.add(name);
            }
            routes.put(name, new RouteDef(name, parent.find() ? parent.group(1) : null, hasPath ? path.group(2) : null));
        }
    }

    @Test
    void everyCreateRouteCallIsParsed() {
        // A typed binding (const r: AnyRoute = createRoute(...)), a generic call or an inline createRoute would
        // otherwise be skipped silently — keep the `const name = createRoute({` form
        assertThat(routes).as("routes parsed as `const name = createRoute({` in router.tsx").hasSize(createRouteCalls);
    }

    @Test
    void everyRouteHasALiteralPathOrId() {
        // A path from a constant or an expression cannot be checked here — keep route paths literal
        assertThat(routesWithoutLiteralPathOrId).as("routes in router.tsx without a literal path or id").isEmpty();
        assertThat(routes.values()).as("routes without getParentRoute").allSatisfy(r -> assertThat(r.parent()).isNotNull());
    }

    @Test
    void everyTopLevelFrontendRouteIsOnTheAllowList() {
        List<String> topLevelPaths = routes.values().stream()
                .filter(r -> !r.pathless() && isTopLevel(r))
                .map(r -> "/" + r.path().replaceFirst("^/", ""))
                .filter(path -> !path.equals("/"))
                .toList();

        // Guards against the parsing silently matching nothing after a router refactoring
        assertThat(topLevelPaths).as("top-level route paths in router.tsx").hasSizeGreaterThanOrEqualTo(7);
        assertThat(topLevelPaths).allSatisfy(path -> assertThat(SpaRoutingHandler.isSpaRoute(path))
                .as("%s is a frontend route but its first segment is not on SpaRoutingHandler.SPA_ROUTE_PREFIXES", path)
                .isTrue());
    }

    /** Every ancestor up to rootRoute is a pathless layout. */
    private static boolean isTopLevel(RouteDef route) {
        String parent = route.parent();
        while (!ROOT_ROUTE.equals(parent)) {
            RouteDef ancestor = routes.get(parent);
            if (ancestor == null || !ancestor.pathless()) {
                return false;
            }
            parent = ancestor.parent();
        }
        return true;
    }
}
