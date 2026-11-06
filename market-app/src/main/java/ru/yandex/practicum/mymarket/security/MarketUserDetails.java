package ru.yandex.practicum.mymarket.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import ru.yandex.practicum.mymarket.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Аутентифицированный покупатель — principal в {@code SecurityContext}. Кроме логина хранит id покупателя,
 * по которому контроллеры находят его корзину и заказы ({@code @AuthenticationPrincipal MarketUserDetails}).
 */
public final class MarketUserDetails implements UserDetails {

    public static final String ROLE_USER = "ROLE_USER";

    private static final List<GrantedAuthority> AUTHORITIES = AuthorityUtils.createAuthorityList(ROLE_USER);

    private final long id;
    private final String username;
    private final String password;

    public MarketUserDetails(long id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    public static MarketUserDetails from(User user) {
        return new MarketUserDetails(user.getId(), user.getUsername(), user.getPassword());
    }

    public long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MarketUserDetails other && id == other.id && username.equals(other.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return "MarketUserDetails[id=" + id + ", username=" + username + "]";
    }
}
