package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import io.jmix.security.model.ResourceRole;
import io.jmix.security.role.ResourceRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import ru.thedevs.testproject.dto.RoleTreeNode;
import ru.thedevs.testproject.view.main.MainView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Route(value = "sec/resourcerolemodelslookup", layout = MainView.class)
@ViewController("sec_ResourceRoleModel.lookup")
@ViewDescriptor("custom-resource-role-model-lookup-view.xml")
@LookupComponent("entitiesTree")
@DialogMode(width = "70em", height = "45em")
public class CustomResourceRoleModelLookupView extends StandardListView<RoleTreeNode> {

    @ViewComponent
    private CollectionContainer<RoleTreeNode> entitiesDc;
    @ViewComponent
    private TreeDataGrid<RoleTreeNode> entitiesTree;

    @Autowired
    private ResourceRoleRepository roleRepository;

    private String nameFilterText = "";
    private String selectedCategory = null;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        loadRoles();

        entitiesDc.addItemPropertyChangeListener(this::onItemPropertyChange);
    }

    private void onItemPropertyChange(InstanceContainer.ItemPropertyChangeEvent<RoleTreeNode> event) {
        if ("enabled".equals(event.getProperty())) {
            RoleTreeNode node = event.getItem();
            Boolean newValue = (Boolean) event.getValue();

            if (node != null && newValue != null) {
                node.setEnabled(newValue);

                updateParentState(node.getParent());

                entitiesDc.setItems(new ArrayList<>(entitiesDc.getItems()));
            }
        }
    }

    private void updateParentState(RoleTreeNode parent) {
        if (parent == null) return;

        boolean allEnabled = parent.getChildren().stream()
                .allMatch(RoleTreeNode::getEnabled);

        boolean allDisabled = parent.getChildren().stream()
                .noneMatch(RoleTreeNode::getEnabled);

        if (allEnabled) {
            parent.setEnabled(true);
        } else if (allDisabled) {
            parent.setEnabled(false);
        } else {
            parent.setEnabled(false);
        }

        updateParentState(parent.getParent());
    }

    private void loadRoles() {
        dumpAllRolesToConsole();

        Map<String, List<ResourceRole>> byPolicyGroup = roleRepository.getAllRoles().stream()
                .collect(Collectors.groupingBy(role -> {
                    if (role.getResourcePolicies() != null && !role.getResourcePolicies().isEmpty()) {
                        String policyGroup = role.getResourcePolicies().iterator().next().getPolicyGroup();
                        if (policyGroup == null) return "Без группы";

                        String normalized = policyGroup
                                .replaceAll("(Edit|Read|Delete|Create)$", "")
                                .replaceAll("Role$", "");
                        normalized = normalized.substring(0, 1).toUpperCase() + normalized.substring(1);

                        return normalized;
                    }
                    return "Без группы";
                }));

        List<RoleTreeNode> groupNodes = byPolicyGroup.entrySet().stream()
                .map(entry -> {
                    String displayName = entry.getValue().get(0).getName().split(":")[0].trim();

                    RoleTreeNode groupNode = RoleTreeNode.group(displayName, "Полиси-группа");

                    List<RoleTreeNode> actionNodes = entry.getValue().stream()
                            .map(role -> {
                                String action = role.getName().contains(":")
                                        ? role.getName().split(":")[1].trim()
                                        : role.getName();

                                RoleTreeNode actionNode = RoleTreeNode.permission(
                                        action,
                                        role.getCode(),
                                        role.getName()
                                );
                                actionNode.setParent(groupNode);
                                return actionNode;
                            })
                            .collect(Collectors.toList());

                    groupNode.setChildren(actionNodes);
                    return groupNode;
                })
                .collect(Collectors.toList());

        List<RoleTreeNode> allNodes = new ArrayList<>();
        groupNodes.forEach(r -> {
            allNodes.add(r);
            allNodes.addAll(flatten(r.getChildren()));
        });

        entitiesDc.setItems(allNodes);
    }

    private void dumpAllRolesToConsole() {
        Collection<ResourceRole> roles = roleRepository.getAllRoles();
        System.out.println("=== Всего ролей: " + (roles != null ? roles.size() : 0) + " ===");
        if (roles == null) return;

        for (ResourceRole role : roles) {
            System.out.println("--------------------------------------------------");
            System.out.println("Role:");
            System.out.println("  name: " + role.getName());
            System.out.println("  code: " + role.getCode());
            System.out.println("  source: " + role.getSource());
            System.out.println("  description: " + role.getDescription());
            System.out.println("  tenantId: " + role.getTenantId());

            System.out.println("  childRoles:");
            if (role.getChildRoles() != null && !role.getChildRoles().isEmpty()) {
                role.getChildRoles().forEach(cr -> System.out.println("    - " + cr));
            } else {
                System.out.println("    (none)");
            }

            System.out.println("  scopes:");
            if (role.getScopes() != null && !role.getScopes().isEmpty()) {
                role.getScopes().forEach(scope -> System.out.println("    - " + scope));
            } else {
                System.out.println("    (none)");
            }

            System.out.println("  resourcePolicies:");
            if (role.getResourcePolicies() != null && !role.getResourcePolicies().isEmpty()) {
                for (var policy : role.getResourcePolicies()) {
                    System.out.println("    - type: " + policy.getType());
                    System.out.println("      resource: " + policy.getResource());
                    System.out.println("      action: " + policy.getAction());
                    System.out.println("      effect: " + policy.getEffect());
                    System.out.println("      policyGroup: " + policy.getPolicyGroup());
                    if (policy.getCustomProperties() != null && !policy.getCustomProperties().isEmpty()) {
                        System.out.println("      customProperties:");
                        policy.getCustomProperties().forEach((k, v) ->
                                System.out.println("        * " + k + " = " + v));
                    }
                }
            } else {
                System.out.println("    (none)");
            }
        }
        System.out.println("=== Конец вывода ролей ===");
    }

    private List<RoleTreeNode> flatten(List<RoleTreeNode> nodes) {
        List<RoleTreeNode> flat = new ArrayList<>();
        for (RoleTreeNode n : nodes) {
            flat.add(n);
            flat.addAll(flatten(n.getChildren()));
        }
        return flat;
    }

    @Subscribe("nameFilter")
    public void onNameFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        nameFilterText = event.getValue() != null ? event.getValue() : "";
        loadRoles();
    }

    @Subscribe("categoryFilter")
    public void onCategoryFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        selectedCategory = event.getValue();
        loadRoles();
    }

    @Subscribe("entitiesTree")
    public void onEntitiesTreeItemDoubleClick(ItemDoubleClickEvent<RoleTreeNode> event) {
        RoleTreeNode clicked = event.getItem();
        if (clicked != null) {
            if (entitiesTree.isExpanded(clicked)) {
                entitiesTree.collapse(clicked);
            } else {
                entitiesTree.expand(clicked);
            }
        }
    }
}
