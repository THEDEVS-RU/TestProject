package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import io.jmix.security.model.ResourceRoleModel;
import io.jmix.security.role.ResourceRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import ru.thedevs.testproject.dto.RoleTreeNode;
import ru.thedevs.testproject.view.main.MainView;

import java.util.List;
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
                    RoleTreeNode group = RoleTreeNode.group(role.getName(), category);

                    List<RoleTreeNode> children = role.getResourcePolicies().stream()
                            .map(p -> RoleTreeNode.permission(
                                    p.getAction(),
                                    p.getResource(),
                                    p.getType()
                            ))
                            .collect(Collectors.toList());

                    children.forEach(c -> c.setParent(group));
                    group.setChildren(children);

                    return group;
                })
                .filter(r -> nameFilterText.isEmpty() ||
                        r.getName().toLowerCase().contains(nameFilterText.toLowerCase()))
                .filter(r -> selectedCategory == null || selectedCategory.equals(r.getCategory()))
                .collect(Collectors.toList());


        List<RoleTreeNode> allNodes = new java.util.ArrayList<>();
        roles.forEach(r -> {
            allNodes.add(r);
            allNodes.addAll(r.getChildren());
        });

        System.out.println("Загружено ролей: " + roles.size());
        entitiesDc.setItems(allNodes);
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
