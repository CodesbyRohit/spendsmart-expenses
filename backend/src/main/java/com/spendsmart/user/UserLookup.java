package com.spendsmart.user;

import com.spendsmart.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Loads the signed-in user's entity; shared by services that need preferences or a managed reference. */
@Component
public class UserLookup {

    private final UserRepository users;

    public UserLookup(UserRepository users) {
        this.users = users;
    }

    public User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Account"));
    }

    /** A lazy reference for foreign keys, avoiding a SELECT when only the id is needed. */
    public User reference(UUID userId) {
        return users.getReferenceById(userId);
    }
}
