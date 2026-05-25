package com.povzetkar.quarkus.builder;

import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.User;

import java.util.UUID;

public class UserBuilder {

    private Long id;
    private String username;
    private String email;
    private String name;
    private AccountStatus accountStatus = AccountStatus.ENABLED;

    private UserBuilder() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        this.username = "user_" + uniqueId;
        this.email = "user_" + uniqueId + "@example.com";
        this.name = "Test User " + uniqueId;
    }

    public static UserBuilder aUser() {
        return new UserBuilder();
    }

    public UserBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public UserBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public UserBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public UserBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public UserBuilder pending() {
        this.accountStatus = AccountStatus.PENDING_APPROVAL;
        return this;
    }

    public UserBuilder withStatus(AccountStatus status) {
        this.accountStatus = status;
        return this;
    }

    public User build() {
        User user = new User();
        user.id = this.id;
        user.username = this.username;
        user.email = this.email;
        user.name = this.name;
        user.accountStatus = this.accountStatus;
        return user;
    }
}
