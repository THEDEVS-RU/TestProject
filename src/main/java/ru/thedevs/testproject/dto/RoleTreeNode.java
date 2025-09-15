package ru.thedevs.testproject.dto;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.InstanceName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JmixEntity(name = "test_RoleTreeNode")
public class RoleTreeNode {

    @JmixId
    private String id = UUID.randomUUID().toString();

    @InstanceName
    private String name;

    private String category;
    private Boolean enabled = true;

    /** "GROUP" или "PERMISSION" */
    private String nodeType;

    private String action;
    private String resource;
    private String permissionType;

    private List<RoleTreeNode> children = new ArrayList<>();

    public static RoleTreeNode group(String name, String category) {
        RoleTreeNode node = new RoleTreeNode();
        node.nodeType = "GROUP";
        node.name = name;
        node.category = category;
        return node;
    }

    public static RoleTreeNode permission(String action, String resource, String type) {
        RoleTreeNode node = new RoleTreeNode();
        node.nodeType = "PERMISSION";
        node.action = action;
        node.resource = resource;
        node.permissionType = type != null ? type : "";
        node.name = action + " : " + resource;
        return node;
    }

    // --- getters/setters ---
    public String getId() { return id; }
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
    public void setId(String id) {
        this.id = id;
    }


    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getAction() { return action; }
    public String getResource() { return resource; }
    public String getPermissionType() { return permissionType; }

    public List<RoleTreeNode> getChildren() { return children; }
    public void setChildren(List<RoleTreeNode> children) { this.children = children; }
    public void addChild(RoleTreeNode child) { this.children.add(child); }
}
