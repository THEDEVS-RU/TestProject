package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import io.jmix.security.model.ResourcePolicyModel;
import io.jmix.security.model.ResourceRoleModel;
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
public class CustomResourceRoleModelLookupView extends StandardListView<ResourceRoleModel> {

    @ViewComponent
    private CollectionContainer<RoleTreeNode> entitiesDc;

    @Autowired
    private ResourceRoleRepository roleRepository;

    private String nameFilterText = "";
    private String selectedCategory = null;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        loadRoles();
    }

    private void loadRoles() {
        List<RoleTreeNode> roles = roleRepository.getAllRoles().stream()
                .map(role -> {
                    String category = role.getCustomProperties()
                            .getOrDefault("category", "Общая");

                    RoleTreeNode roleNode = RoleTreeNode.group(role.getName(), category);

                    // группировка по resource
                    Map<String, List<ResourcePolicyModel>> byResource = role.getResourcePolicies().stream()
                            .map(p -> {
                                ResourcePolicyModel rpm = new ResourcePolicyModel();
                                rpm.setAction(p.getAction());
                                rpm.setResource(p.getResource());
                                rpm.setType(p.getType());
                                return rpm;
                            })
                            .collect(Collectors.groupingBy(ResourcePolicyModel::getResource));

                    List<RoleTreeNode> resourceNodes = byResource.entrySet().stream()
                            .map(entry -> {
                                String resource = entry.getKey();
                                RoleTreeNode resourceNode = RoleTreeNode.group(resource, category);
                                resourceNode.setParent(roleNode);

                                // действия сразу внутри ресурса
                                List<RoleTreeNode> actionNodes = entry.getValue().stream()
                                        .map(p -> {
                                            RoleTreeNode actionNode = RoleTreeNode.permission(
                                                    p.getAction(),
                                                    p.getResource(),
                                                    p.getType()
                                            );
                                            actionNode.setParent(resourceNode);
                                            return actionNode;
                                        })
                                        .collect(Collectors.toList());

                                resourceNode.setChildren(actionNodes);
                                return resourceNode;
                            })
                            .collect(Collectors.toList());

                    roleNode.setChildren(resourceNodes);
                    return roleNode;
                })
                .collect(Collectors.toList());

        // плоский список для контейнера
        List<RoleTreeNode> allNodes = new ArrayList<>();
        roles.forEach(r -> {
            allNodes.add(r);
            allNodes.addAll(flatten(r.getChildren()));
        });

        entitiesDc.setItems(allNodes);
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
}
