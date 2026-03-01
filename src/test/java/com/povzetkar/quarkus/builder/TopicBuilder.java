package com.povzetkar.quarkus.builder;

import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;

import java.util.UUID;

public class TopicBuilder {

    private String name;
    private User user;
    private boolean enabled = true;

    private TopicBuilder() {
        // Defaults
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        this.name = "Test Topic " + uniqueId;
    }

    public static TopicBuilder aTopic() {
        return new TopicBuilder();
    }

    public TopicBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public TopicBuilder withUser(User user) {
        this.user = user;
        return this;
    }

    public TopicBuilder disabled() {
        this.enabled = false;
        return this;
    }

    public Topic build() {
        Topic topic = new Topic();
        topic.name = this.name;

        if (this.user == null) {
            throw new IllegalStateException(
                    "A persisted User must be explicitly provided via .withUser() to avoid TransientPropertyValueException.");
        }
        topic.user = this.user;

        topic.enabled = this.enabled;
        return topic;
    }
}
