package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import io.jmix.security.model.ResourceRole;
import io.jmix.security.role.ResourceRoleRepository;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.jmix.security.model.RoleSource;
import org.springframework.beans.factory.annotation.Autowired;
import ru.thedevs.entities.UserEntity;
import ru.thedevs.service.ICoreUtilsService;
import ru.thedevs.testproject.dto.RoleTreeNode;
import ru.thedevs.testproject.entity.User;
import ru.thedevs.testproject.view.main.MainView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
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

    @ViewComponent
    private TextField nameFilter;

    @ViewComponent
    private ComboBox<String> categoryFilter;

    @Autowired
    private ResourceRoleRepository roleRepository;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private Metadata metadata;

    private String subjectUsername = null;

    private final Set<String> existingAssignedRoleCodes = new HashSet<>();
    private final Set<String> pendingSelectedRoleCodes = new LinkedHashSet<>();

    private boolean isAdjustingSelection = false;
    private boolean selectionListenerAttached = false;

    private Set<RoleTreeNode> lastSelected = new LinkedHashSet<>();

    private final List<RoleTreeNode> initialGroups = new ArrayList<>();
    private final List<RoleTreeNode> initialNodes = new ArrayList<>();

    private boolean nameFilterListenerAttached = false;
    private boolean categoryFilterListenerAttached = false;

    private boolean filterAdjusting = false;

    public void setSubjectUsername(String subjectUsername) {
        this.subjectUsername = subjectUsername;
    }

    public void setExistingAssignedRoleCodes(Collection<String> codes) {
        this.existingAssignedRoleCodes.clear();
        if (codes != null) {
            this.existingAssignedRoleCodes.addAll(codes);
        }
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        loadRoles();
        setupColumnsAndAssigned();
        loadAssignmentsFromDb();

        pendingSelectedRoleCodes.clear();
        pendingSelectedRoleCodes.addAll(existingAssignedRoleCodes);

        if (categoryFilter != null) {
            categoryFilter.setItems("Системные", "Пользовательские");
            categoryFilter.setClearButtonVisible(true);
        }

        attachFilterListenersIfNeeded();

        applyInitialSelectionFromPending();
    }

    private void attachFilterListenersIfNeeded() {
        if (nameFilter != null && !nameFilterListenerAttached) {
            nameFilter.addValueChangeListener((HasValue.ValueChangeEvent<String> e) -> {
                if (filterAdjusting) return;
                String q = e.getValue();
                String category = categoryFilter != null ? categoryFilter.getValue() : null;
                applyFilter(q, category);
            });
            nameFilterListenerAttached = true;
        }

        if (categoryFilter != null && !categoryFilterListenerAttached) {
            categoryFilter.addValueChangeListener((HasValue.ValueChangeEvent<String> e) -> {
                if (filterAdjusting) return;
                String category = e.getValue();
                String q = nameFilter != null ? nameFilter.getValue() : null;
                applyFilter(q, category);
            });
            categoryFilterListenerAttached = true;
        }
    }

    private void loadRoles() {
        Map<String, List<ResourceRole>> byPolicyGroup = roleRepository.getAllRoles().stream()
                .collect(Collectors.groupingBy(role -> {
                    if (role.getResourcePolicies() != null && !role.getResourcePolicies().isEmpty()) {
                        String policyGroup = role.getResourcePolicies().iterator().next().getPolicyGroup();
                        if (policyGroup == null) return "Без группы";

                        String normalized = policyGroup
                                .replaceAll("(Edit|Read|Delete|Create)$", "")
                                .replaceAll("Role$", "");
                        if (normalized.isEmpty()) return "Без группы";
                        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
                    }
                    return "Без группы";
                }));

        List<RoleTreeNode> groupNodes = byPolicyGroup.entrySet().stream()
                .map(entry -> {
                    boolean anyHasUniqueKey = entry.getValue().stream().anyMatch(this::hasUniqueKeyInAnyPolicy);
                    String category = anyHasUniqueKey ? "Системные" : "Пользовательские";

                    String displayName = entry.getValue().get(0).getName().split(":")[0].trim();
                    RoleTreeNode groupNode = RoleTreeNode.group(displayName, category);
                    groupNode.setCategory(category);

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

                                try {
                                    String source = role.getSource();
                                    boolean assignable = RoleSource.DATABASE.equals(source) || RoleSource.ANNOTATED_CLASS.equals(source);
                                    actionNode.setEnabled(assignable);
                                } catch (Throwable ignored) {
                                    actionNode.setEnabled(true);
                                }

                                actionNode.setParent(groupNode);
                                actionNode.setCategory(category);
                                return actionNode;
                            })
                            .collect(Collectors.toList());

                    actionNodes.forEach(groupNode::addChild);
                    return groupNode;
                })
                .collect(Collectors.toList());

        initialGroups.clear();
        initialGroups.addAll(groupNodes);

        List<RoleTreeNode> allFlat = new ArrayList<>();
        for (RoleTreeNode g : groupNodes) {
            allFlat.add(g);
            allFlat.addAll(flatten(g.getChildren()));
        }

        initialNodes.clear();
        initialNodes.addAll(allFlat);

        entitiesDc.setItems(new ArrayList<>(initialNodes));
    }

    private List<RoleTreeNode> flatten(List<RoleTreeNode> nodes) {
        List<RoleTreeNode> flat = new ArrayList<>();
        if (nodes == null) return flat;
        for (RoleTreeNode n : nodes) {
            flat.add(n);
            flat.addAll(flatten(n.getChildren()));
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private void setupColumnsAndAssigned() {
        List<Grid.Column<RoleTreeNode>> current = new ArrayList<>(entitiesTree.getColumns());
        for (Grid.Column<RoleTreeNode> c : current) {
            entitiesTree.removeColumn(c);
        }

        Grid.Column<RoleTreeNode> nameCol;
        try {
            nameCol = entitiesTree.addHierarchyColumn(RoleTreeNode::getName)
                    .setHeader("Название")
                    .setKey("name")
                    .setFlexGrow(2);
        } catch (Throwable t) {
            nameCol = entitiesTree.addColumn(RoleTreeNode::getName)
                    .setHeader("Название")
                    .setKey("name")
                    .setFlexGrow(2);
        }

        Grid.Column<RoleTreeNode> categoryCol = entitiesTree.addColumn(RoleTreeNode::getCategory)
                .setHeader("Категория")
                .setKey("category");

        entitiesTree.setColumnOrder(nameCol, categoryCol);

        if (!selectionListenerAttached) {
            entitiesTree.addSelectionListener(event -> {
                if (isAdjustingSelection) return;

                Set<RoleTreeNode> currentSelected = new LinkedHashSet<>(entitiesTree.getSelectedItems());

                Set<RoleTreeNode> added = new LinkedHashSet<>(currentSelected);
                added.removeAll(lastSelected);

                Set<RoleTreeNode> removed = new LinkedHashSet<>(lastSelected);
                removed.removeAll(currentSelected);

                Set<RoleTreeNode> desired = new LinkedHashSet<>(currentSelected);

                for (RoleTreeNode a : added) {
                    if ("GROUP".equals(a.getNodeType())) {
                        a.setVisualAssigned(true, false);
                        try {
                            entitiesTree.expand(a);
                        } catch (Exception ignored) { }
                        List<RoleTreeNode> perms = gatherPermissionDescendants(a);
                        desired.addAll(perms);
                        for (RoleTreeNode p : perms) {
                            p.setVisualAssigned(true, false);
                        }
                    }
                }

                for (RoleTreeNode r : removed) {
                    if ("GROUP".equals(r.getNodeType())) {
                        r.setVisualAssigned(false, false);

                        List<RoleTreeNode> perms = gatherPermissionDescendants(r);
                        desired.removeAll(perms);
                        for (RoleTreeNode p : perms) {
                            p.setVisualAssigned(false, false);
                        }
                    } else if ("PERMISSION".equals(r.getNodeType())) {
                        r.setVisualAssigned(false, false);
                    }
                }

                Set<RoleTreeNode> desiredPerms = desired.stream()
                        .filter(n -> "PERMISSION".equals(n.getNodeType()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                updatePendingFromSelection(desiredPerms);

                isAdjustingSelection = true;
                try {
                    entitiesTree.deselectAll();
                    for (RoleTreeNode it : desired) {
                        entitiesTree.select(it);
                    }
                    syncAssignmentsForAll();
                    refreshNodes(desiredPerms);
                } finally {
                    isAdjustingSelection = false;
                }

                lastSelected = new LinkedHashSet<>(entitiesTree.getSelectedItems());
            });
            selectionListenerAttached = true;
        }
    }

    private void updatePendingFromSelection(Collection<RoleTreeNode> selectedPermissions) {
        Set<String> selectedCodes = selectedPermissions.stream()
                .filter(n -> "PERMISSION".equals(n.getNodeType()))
                .map(n -> n.getCode() != null ? n.getCode() : n.getResource())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        pendingSelectedRoleCodes.clear();
        pendingSelectedRoleCodes.addAll(selectedCodes);
    }

    private void syncAssignmentsForAll() {
        Collection<RoleTreeNode> all = entitiesDc.getItems();
        if (all == null) return;

        for (RoleTreeNode item : all) {
            if ("PERMISSION".equals(item.getNodeType())) {
                String code = item.getCode() != null ? item.getCode() : item.getResource();
                boolean sel = code != null && pendingSelectedRoleCodes.contains(code);
                item.setAssigned(sel, false);
                item.setVisualAssigned(sel, false);
            }
        }

        for (RoleTreeNode item : all) {
            if ("GROUP".equals(item.getNodeType())) {
                boolean allAssigned = item.getChildren() != null &&
                        item.getChildren().stream().allMatch(c -> Boolean.TRUE.equals(c.getAssigned()));
                item.setAssigned(allAssigned, false);
            }
        }
    }

    private void refreshNodes(Collection<RoleTreeNode> nodes) {
        if (nodes == null) return;
        for (RoleTreeNode n : nodes) {
            try {
                entitiesTree.getDataProvider().refreshItem(n);
                if (n.getParent() != null) {
                    entitiesTree.getDataProvider().refreshItem(n.getParent());
                }
            } catch (Exception ignored) { }
        }
    }

    private List<RoleTreeNode> gatherPermissionDescendants(RoleTreeNode node) {
        List<RoleTreeNode> result = new ArrayList<>();
        if (node == null) return result;
        if ("PERMISSION".equals(node.getNodeType())) {
            result.add(node);
        }
        if (node.getChildren() != null) {
            for (RoleTreeNode c : node.getChildren()) {
                result.addAll(gatherPermissionDescendants(c));
            }
        }
        return result;
    }

    private void loadAssignmentsFromDb() {
        existingAssignedRoleCodes.clear();
        if (subjectUsername == null || subjectUsername.isEmpty()) return;

        List<RoleAssignmentEntity> assignments = dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :username and r.roleType = :type")
                .parameter("username", subjectUsername)
                .parameter("type", RoleAssignmentRoleType.RESOURCE)
                .list();

        assignments.stream()
                .map(RoleAssignmentEntity::getRoleCode)
                .filter(Objects::nonNull)
                .forEach(existingAssignedRoleCodes::add);
    }


    private void applyInitialSelectionFromPending() {
        Collection<RoleTreeNode> all = entitiesDc.getItems();
        if (all == null) return;

        for (RoleTreeNode node : all) {
            if ("PERMISSION".equals(node.getNodeType())) {
                String code = node.getCode() != null ? node.getCode() : node.getResource();
                boolean sel = code != null && pendingSelectedRoleCodes.contains(code);
                node.setAssigned(sel, false);
                node.setVisualAssigned(sel, false);
            }
        }

        for (RoleTreeNode node : all) {
            if ("GROUP".equals(node.getNodeType())) {
                boolean allChildrenVisual = node.getChildren() != null &&
                        node.getChildren().stream().allMatch(c -> Boolean.TRUE.equals(c.getVisualAssigned()));
                node.setVisualAssigned(allChildrenVisual, false);
                boolean allChildrenAssigned = node.getChildren() != null &&
                        node.getChildren().stream().allMatch(c -> Boolean.TRUE.equals(c.getAssigned()));
                node.setAssigned(allChildrenAssigned, false);
            }
        }

        Set<RoleTreeNode> toSelect = all.stream()
                .filter(n -> "PERMISSION".equals(n.getNodeType()))
                .filter(n -> {
                    String code = n.getCode() != null ? n.getCode() : n.getResource();
                    return code != null && pendingSelectedRoleCodes.contains(code);
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        isAdjustingSelection = true;
        try {
            entitiesTree.deselectAll();
            toSelect.forEach(entitiesTree::select);
            refreshNodes(toSelect);
            lastSelected = new LinkedHashSet<>(entitiesTree.getSelectedItems());
        } finally {
            isAdjustingSelection = false;
        }
    }

    private void applyFilter(String text, String category) {
        if (initialGroups.isEmpty()) {
            loadRoles();
            setupColumnsAndAssigned();
        }

        String q = text != null ? text.trim() : "";
        String qLower = q.toLowerCase();

        List<RoleTreeNode> matchedGroups = initialGroups.stream()
                .filter(g -> (q.isEmpty() || (g.getName() != null && g.getName().toLowerCase().contains(qLower))))
                .filter(g -> (category == null || category.isEmpty() || category.equals(g.getCategory())))
                .collect(Collectors.toList());

        List<RoleTreeNode> displayList = new ArrayList<>();
        for (RoleTreeNode g : matchedGroups) {
            displayList.add(g);
            List<RoleTreeNode> childrenFlat = flatten(g.getChildren());
            if (!childrenFlat.isEmpty()) displayList.addAll(childrenFlat);
        }

        entitiesDc.setItems(displayList);
        setupColumnsAndAssigned();
        syncAssignmentsForAll();
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

    private boolean hasUniqueKeyInAnyPolicy(ResourceRole role) {
        if (role == null) return false;
        try {
            Collection<?> policies = role.getResourcePolicies();
            if (policies == null) return false;
            for (Object p : policies) {
                if (p == null) continue;
                try {
                    Method m = p.getClass().getMethod("getCustomProperties");
                    Object cp = m.invoke(p);
                    if (cp instanceof Map) {
                        if (((Map<?, ?>) cp).containsKey("uniqueKey")) return true;
                    } else if (cp != null && cp.toString().contains("uniqueKey")) {
                        return true;
                    }
                } catch (NoSuchMethodException nsme) {
                    try {
                        Field f = p.getClass().getDeclaredField("customProperties");
                        f.setAccessible(true);
                        Object cp = f.get(p);
                        if (cp instanceof Map) {
                            if (((Map<?, ?>) cp).containsKey("uniqueKey")) return true;
                        } else if (cp != null && cp.toString().contains("uniqueKey")) {
                            return true;
                        }
                    } catch (Throwable ignored) { }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return false;
    }
}
