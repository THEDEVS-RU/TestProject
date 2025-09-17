package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import io.jmix.security.model.ResourceRole;
import io.jmix.security.role.ResourceRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import ru.thedevs.testproject.dto.RoleTreeNode;
import ru.thedevs.testproject.view.main.MainView;

import java.util.ArrayList;
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
    }

    private void loadRoles() {
//        dumpAllRolesToConsole();

        Map<String, List<ResourceRole>> byEntity = roleRepository.getAllRoles().stream()
                .collect(Collectors.groupingBy(role -> {
                    String name = role.getName();
                    if (name.contains(":")) {
                        return name.split(":")[0].trim();
                    }
                    return name.trim();
                }));

        List<RoleTreeNode> entityNodes = byEntity.entrySet().stream()
                .map(entry -> {
                    String entityName = entry.getKey();

                    RoleTreeNode entityNode = RoleTreeNode.group(entityName, "Общая");

                    List<RoleTreeNode> actionNodes = entry.getValue().stream()
                            .map(role -> {
                                String action = role.getName().contains(":")
                                        ? role.getName().split(":")[1].trim()
                                        : role.getName();

                                RoleTreeNode actionNode = RoleTreeNode.permission(action, role.getCode(), role.getName());
                                actionNode.setParent(entityNode);
                                return actionNode;
                            })
                            .collect(Collectors.toList());

                    entityNode.setChildren(actionNodes);
                    return entityNode;
                })
                .collect(Collectors.toList());

        List<RoleTreeNode> allNodes = new ArrayList<>();
        entityNodes.forEach(r -> {
            allNodes.add(r);
            allNodes.addAll(flatten(r.getChildren()));
        });

        entitiesDc.setItems(allNodes);
    }


//    private void dumpAllRolesToConsole() {
//        Collection<ResourceRole> roles = roleRepository.getAllRoles();
//        System.out.println("=== Всего ролей: " + (roles != null ? roles.size() : 0) + " ===");
//        if (roles == null) return;
//
//        for (ResourceRole role : roles) {
//            System.out.println("--------------------------------------------------");
//            System.out.println("Role:");
//            System.out.println("  name: " + String.valueOf(role.getName()));
//            System.out.println("  code: " + String.valueOf(role.getCode()));
//            System.out.println("  source: " + String.valueOf(role.getSource()));
//            System.out.println("  description: " + String.valueOf(role.getDescription()));
//            System.out.println("  tenantId: " + String.valueOf(role.getTenantId()));
//
//            System.out.println("  childRoles:");
//            if (role.getChildRoles() != null && !role.getChildRoles().isEmpty()) {
//                for (String cr : role.getChildRoles()) {
//                    System.out.println("    - " + cr);
//                }
//            } else {
//                System.out.println("    (none)");
//            }
//        }
//        System.out.println("=== Конец вывода ролей ===");
//    }

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
