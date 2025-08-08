package ru.thedevs.testproject.entity;

import io.jmix.core.entity.annotation.ReplaceEntity;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.security.authentication.JmixUserDetails;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.springframework.security.core.GrantedAuthority;
import ru.thedevs.entities.BaseUser;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

@JmixEntity
@Entity
@ReplaceEntity(BaseUser.class)
public class User extends BaseUser implements JmixUserDetails, Serializable {

    @Transient
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities != null ? authorities : Collections.emptyList();
    }

    @Override
    public void setAuthorities(final Collection<? extends GrantedAuthority> authorities) {
        this.authorities = authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(active);
    }

    @InstanceName
    @DependsOnProperties({"username"})
    public String getDisplayName() {
        return String.format("[%s]", username).trim();
    }

    @Override
    public String getTimeZoneId() {
        return timeZoneId;
    }

    @Override
    public boolean isAutoTimeZone() {
        return true;
    }

}