package ru.thedevs.testproject.dto;

import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import io.jmix.security.model.ResourcePolicy;

@JmixEntity(name = "sec_RolePermissionNode")
public class RolePermissionNode {

    private ResourcePolicy policy;

    @JmixProperty
    private boolean enabled = true;

    public RolePermissionNode(ResourcePolicy policy) {
        this.policy = policy;
    }

    @JmixProperty
    public String getAction() {
        return policy.getAction();
    }

    @JmixProperty
    public String getResource() {
        return policy.getResource();
    }

    @JmixProperty
    public String getType() {
        return policy.getType() != null ? policy.getType() : "";
    }

    public ResourcePolicy getPolicy() {
        return policy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
