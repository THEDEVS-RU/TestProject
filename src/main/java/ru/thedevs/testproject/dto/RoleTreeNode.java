package ru.thedevs.testproject.dto;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.security.model.ResourceRoleModel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JmixEntity(name = "test_RoleTreeNode")
public class RoleTreeNode extends ResourceRoleModel {

    @JmixId
    private UUID id = UUID.randomUUID();

    @InstanceName
    private String name;

    private String category;
    private Boolean enabled = true;

    /** "GROUP" или "PERMISSION" */
    private String nodeType;


    private Boolean assigned = false;

    private Boolean visualAssigned = false; // для названий(узлов) ролей

    private String action;
    private String resource;
    private String permissionType;
    private RoleTreeNode parent;

    private List<RoleTreeNode> children = new ArrayList<>();

    public static RoleTreeNode group(String name, String category) {
        RoleTreeNode node = new RoleTreeNode();
        node.nodeType = "GROUP";
        node.name = name;
        node.category = category;
        node.assigned = false;
        node.visualAssigned = false;
        return node;
    }

    public static RoleTreeNode permission(String action, String code, String type) {
        RoleTreeNode node = new RoleTreeNode();
        node.nodeType = "PERMISSION";
        node.action = action;
        node.setCode(code);
        node.resource = code;
        node.permissionType = type != null ? type : "";
        node.name = action;
        node.assigned = false;
        node.visualAssigned = false;

        try {
            if (code != null) {
                node.id = UUID.nameUUIDFromBytes(code.getBytes(StandardCharsets.UTF_8));
            } else {
                node.id = UUID.randomUUID();
            }
        } catch (Throwable ignored) {
            node.id = UUID.randomUUID();
        }
        return node;
    }

    // --- getters/setters ---

    public Boolean getAssigned() {
        return assigned;
    }

    public void setAssigned(Boolean assigned) {
        setAssigned(assigned, true);
    }

    public void setAssigned(Boolean assigned, boolean propagateDown) {
        this.assigned = assigned != null ? assigned : Boolean.FALSE;
        if (propagateDown && children != null) {
            for (RoleTreeNode c : children) {
                c.setAssigned(this.assigned, true);
            }
        }
    }

    public Boolean getVisualAssigned() {
        return visualAssigned;
    }

    public void setVisualAssigned(Boolean visualAssigned) {
        setVisualAssigned(visualAssigned, true);
    }

    public void setVisualAssigned(Boolean visualAssigned, boolean propagateDown) {
        this.visualAssigned = visualAssigned != null ? visualAssigned : Boolean.FALSE;
        if (propagateDown && children != null) {
            for (RoleTreeNode c : children) {
                c.setVisualAssigned(this.visualAssigned, true);
            }
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        if (children != null) {
            children.forEach(c -> c.setEnabled(enabled));
        }
    }

    public RoleTreeNode getParent() { return parent; }
    public void setParent(RoleTreeNode parent) { this.parent = parent; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getAction() { return action; }
    public String getResource() { return resource; }
    public String getPermissionType() { return permissionType; }

    public List<RoleTreeNode> getChildren() { return children; }
    public void setChildren(List<RoleTreeNode> children) { this.children = children; }
    public void addChild(RoleTreeNode child) {
        if (child != null) {
            child.setParent(this);
            this.children.add(child);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleTreeNode that = (RoleTreeNode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
