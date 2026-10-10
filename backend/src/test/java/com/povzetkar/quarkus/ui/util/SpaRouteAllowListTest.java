package com.povzetkar.quarkus.ui.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.povzetkar.quarkus.test.util.RepositoryFiles.findInRepository;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the SPA fallback allow-list against the frontend router (POV-221): {@link SpaRoutingHandler#SPA_ROUTES}
 * must be exactly the URL patterns of {@code frontend/src/router.tsx} — a missing pattern would answer 404 on
 * refresh or as a deep link, an extra one would serve the shell for a path the router does not know.
 * <p>
 * Each {@code createRoute(...)} is read with its parent and its literal {@code path} or {@code id}; a route's URL
 * joins its ancestors' paths, where pathless layouts (an {@code id}, such as the protected layout) add nothing.
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
    void routesAreDefinedOnlyInRouterTsx() throws IOException {
        // A route defined in another file would escape this guard; createLazyRoute only attaches a component to a
        // route defined here and is fine
        Path router = findInRepository("frontend/src/router.tsx");
        Pattern routeDefinition = Pattern.compile("\\b(createRoute|createFileRoute)\\s*[<(]");
        List<Path> elsewhere;
        try (Stream<Path> files = Files.walk(router.getParent())) {
            elsewhere = files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().matches(".*\\.tsx?$") && !file.equals(router))
                    .filter(file -> {
                        try {
                            return routeDefinition.matcher(Files.readString(file, StandardCharsets.UTF_8)).find();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
        assertThat(elsewhere).as("files under frontend/src that define routes outside router.tsx").isEmpty();
    }

    @Test
    void everyRouteHasALiteralPathOrId() {
        // A path from a constant or an expression cannot be checked here — keep route paths literal
        assertThat(routesWithoutLiteralPathOrId).as("routes in router.tsx without a literal path or id").isEmpty();
        assertThat(routes.values()).as("routes without getParentRoute").allSatisfy(r -> assertThat(r.parent()).isNotNull());
    }

    @Test
    void everyRouteSegmentIsOneTheHandlerCanMatch() {
        // SpaRoutingHandler matches lower-case literal segments and single-segment $params only: an upper-case
        // literal, a splat ($) or an optional param ({-$id}) would pass the mirror check below and still 404 on
        // refresh — extend SpaRoutingHandler.matches first
        Pattern supported = Pattern.compile("[a-z0-9-]+|\\$[A-Za-z]\\w*");
        List<String> unsupported = routes.values().stream()
                .filter(r -> !r.pathless())
                .flatMap(r -> Arrays.stream(r.path().split("/"))
                        .filter(segment -> !segment.isEmpty() && !supported.matcher(segment).matches())
                        .map(segment -> r.name() + ": '" + segment + "'"))
                .toList();
        assertThat(unsupported).as("route path segments SpaRoutingHandler cannot match").isEmpty();
    }

    @Test
    void allowListMirrorsTheRouterPaths() {
        // Every routed URL, its $params as *: a missing entry would 404 on refresh or as a deep link, an extra one
        // would serve the shell for a path the router does not know
        Set<String> routerPatterns = routes.values().stream()
                .filter(r -> !r.pathless())
                .map(SpaRouteAllowListTest::fullPattern)
                .filter(pattern -> !pattern.equals("/"))
                .collect(Collectors.toSet());

        // Guards against the parsing silently matching nothing after a router refactoring
        assertThat(routerPatterns).as("routed paths in router.tsx").hasSizeGreaterThanOrEqualTo(10);
        assertThat(SpaRoutingHandler.SPA_ROUTES).as("SpaRoutingHandler.SPA_ROUTES vs. router.tsx")
                .containsExactlyInAnyOrderElementsOf(routerPatterns);
    }

    /** The URL pattern of a route: its ancestors' paths joined (pathless layouts add nothing), $params as *. */
    private static String fullPattern(RouteDef route) {
        List<String> segments = new ArrayList<>();
        for (RouteDef r = route; r != null; r = ROOT_ROUTE.equals(r.parent()) ? null : routes.get(r.parent())) {
            if (!r.pathless()) {
                List<String> own = Arrays.stream(r.path().split("/")).filter(s -> !s.isEmpty())
                        .map(s -> s.startsWith("$") ? "*" : s)
                        .toList();
                segments.addAll(0, own);
            }
        }
        return "/" + String.join("/", segments);
    }
}
