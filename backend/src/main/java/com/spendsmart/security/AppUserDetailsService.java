package com.spendsmart.security;

import com.spendsmart.common.Text;
import com.spendsmart.user.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Used only during password verification; the session principal is {@link AuthenticatedUser}. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(Text.normalizeEmail(email))
                .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles("USER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
