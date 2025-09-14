package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import io.jmix.security.model.ResourceRoleModel;
import io.jmix.security.role.ResourceRoleRepository;
import io.jmix.security.model.RoleModelConverter;
import org.springframework.beans.factory.annotation.Autowired;
import ru.thedevs.testproject.dto.RoleGroupNode;
import ru.thedevs.testproject.dto.RolePermissionNode;
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
    private CollectionContainer<RoleGroupNode> entitiesDc;


    @Autowired
    private ResourceRoleRepository roleRepository;
    @Autowired
    private RoleModelConverter roleModelConverter;
    @Autowired
    private UiComponents uiComponents;

    private String nameFilterText = "";
    private String selectedCategory = null;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        loadRoles();
    }

    private void loadRoles() {
        List<RoleGroupNode> roles = roleRepository.getAllRoles().stream()
                .map(role -> {
                    RoleGroupNode node = new RoleGroupNode(role.getName(), role);
                    role.getResourcePolicies().forEach(p ->
                            node.getChildren().add(new RolePermissionNode(p))
                    );
                    return node;
                })
                .filter(r -> nameFilterText.isEmpty() ||
                        r.getName().toLowerCase().contains(nameFilterText.toLowerCase()))
                .filter(r -> selectedCategory == null || selectedCategory.equals(r.getCategory()))
                .collect(Collectors.toList());

        entitiesDc.setItems(roles);
    }




    // 🔍 обработка фильтра по имени
    @Subscribe("nameFilter")
    public void onNameFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        nameFilterText = event.getValue() != null ? event.getValue() : "";
        loadRoles();
    }

    // 🔽 обработка фильтра по категории
    @Subscribe("categoryFilter")
    public void onCategoryFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        selectedCategory = event.getValue();
        loadRoles();
    }
}
