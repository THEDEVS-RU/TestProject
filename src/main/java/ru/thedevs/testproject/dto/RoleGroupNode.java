package ru.thedevs.testproject.dto;

import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import io.jmix.security.model.ResourceRole;

import java.util.ArrayList;
import java.util.List;

@JmixEntity(name = "sec_RoleGroupNode")
public class RoleGroupNode {

    @JmixProperty
    private String name;

    private ResourceRole role;

    @JmixProperty
    private String category;

    @JmixProperty
    private Boolean enabled = true;

    @JmixProperty
    private List<RolePermissionNode> children = new ArrayList<>();

    public RoleGroupNode(String name, ResourceRole role) {
        this.name = name;
        this.role = role;
        this.category = role.getCustomProperties().getOrDefault("category", "Общая");
    }

    public String getName() {
        return name;
    }

    public ResourceRole getRole() {
        return role;
    }

    public List<RolePermissionNode> getChildren() {
        return children;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        role.getCustomProperties().put("category", category);
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        // прокинем состояние во все children
        children.forEach(c -> c.setEnabled(enabled));
    }
}
