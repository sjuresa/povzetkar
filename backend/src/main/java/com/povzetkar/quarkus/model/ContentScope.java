package com.povzetkar.quarkus.model;

import java.util.Objects;

/**
 * Which sources and content items a caller may see (POV-219).
 * <p>
 * {@link #of(User)} restricts reads to the viewer's topics (and, for items, their briefings);
 * {@link #unrestricted()} is the admin view. The constructor is private, so an unrestricted scope
 * has to be asked for explicitly — a missing viewer can never widen the result.
 */
public final class ContentScope {

    private static final ContentScope UNRESTRICTED_SCOPE = new ContentScope(null);

    private final User viewer;

    private ContentScope(User viewer) {
        this.viewer = viewer;
    }

    public static ContentScope unrestricted() {
        return UNRESTRICTED_SCOPE;
    }

    public static ContentScope of(User viewer) {
        return new ContentScope(Objects.requireNonNull(viewer, "viewer"));
    }

    public boolean isUnrestricted() {
        return viewer == null;
    }

    /**
     * @return the viewer of a restricted scope
     * @throws IllegalStateException for the unrestricted scope, which has no viewer
     */
    public User viewer() {
        if (viewer == null) {
            throw new IllegalStateException("The unrestricted scope has no viewer");
        }
        return viewer;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ContentScope that && Objects.equals(viewer, that.viewer));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(viewer);
    }

    @Override
    public String toString() {
        return viewer == null ? "ContentScope[unrestricted]" : "ContentScope[viewer=" + viewer.id + "]";
    }
}
