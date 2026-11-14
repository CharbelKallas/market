package com.market.model.user;

import com.market.model.order.Order;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "\"user\"")
public class User {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String username;

    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "verified_date")
    private Date verifiedDate;

    /** Embedded in each JWT; bumping it (on password change) invalidates all earlier tokens. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    /**
     * Only for the bootstrapped admin: a hash of the ADMIN_PASSWORD value last applied, so a restart re-applies the
     * environment password only when it changed, not when the admin changed their password through the API.
     */
    @Column(name = "bootstrap_password_hash")
    private String bootstrapPasswordHash;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<UserOtp> userOtps;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<Order> orders;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<UserRole> userRoles;

    /**
     * Usernames and emails are stored trimmed and lower-case, so "Bob@x.com" and "bob@x.com" are the same account.
     */
    public static String normalizeIdentifier(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

}
