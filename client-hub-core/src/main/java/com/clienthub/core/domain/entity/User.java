package com.clienthub.core.domain.entity;

import com.clienthub.common.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

@Entity
@Table(name = "users", schema = "core")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false)
    @JsonIgnore  // ✅ Never expose password in JSON responses
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; //ADMIN, CLIENT, FREELANCER

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "wallet_address", unique = true)
    private String walletAddress;

    public enum Role {
        ADMIN,
        FREELANCER,
        CLIENT
    }

    //CONSTRUCTOR
    public User(){
    }

    public User(String id, String email, String password, String fullName, Role role, boolean active, String walletAddress) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
        this.walletAddress = walletAddress;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return password;
    }

    public void setPassword(String password){
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    //BUILDER PATTERN

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private String id;
        private String email;
        private String password;
        private String fullName;
        private Role role;
        private boolean active = true;
        private String walletAddress;

        UserBuilder() {

        }

        public UserBuilder id(String id) {
            this.id = id;
            return this;
        }

        public UserBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserBuilder password(String password) {
            this.password = password;
            return this;
        }

        public UserBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public UserBuilder role(Role role) {
            this.role = role;
            return this;
        }

        public UserBuilder active(boolean active) {
            this.active = active;
            return this;
        }

        public UserBuilder walletAddress(String walletAddress) {
            this.walletAddress = walletAddress;
            return this;
        }

        public User build() {
            return new User(id, email, password, fullName, role, active, walletAddress);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    /**
     * ToString without password for logging and debugging.
     */
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role=" + role +
                ", active=" + active +
                ", walletAddress='" + walletAddress + '\'' +
                '}';
    }
}
