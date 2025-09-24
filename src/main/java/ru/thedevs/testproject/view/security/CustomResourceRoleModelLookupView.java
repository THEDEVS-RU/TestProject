package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
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
import ru.thedevs.testproject.dto.RoleTreeNode;
import ru.thedevs.testproject.view.main.MainView;

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

    @Autowired
    private ResourceRoleRepository roleRepository;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private Metadata metadata;

    private String nameFilterText = "";
    private String selectedCategory = null;

    private String subjectUsername = null;

    private final Set<String> existingAssignedRoleCodes = new HashSet<>();

    private boolean isAdjustingSelection = false;
    private boolean selectionListenerAttached = false;

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
        applyInitialSelectionFromExistingAssignments();
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

                                try {
                                    String source = role.getSource();
                                    boolean assignable = RoleSource.DATABASE.equals(source) || RoleSource.ANNOTATED_CLASS.equals(source);
                                    actionNode.setEnabled(assignable);
                                } catch (Throwable ignored) {
                                    actionNode.setEnabled(true);
                                }

                                actionNode.setParent(groupNode);
                                return actionNode;
                            })
                            .collect(Collectors.toList());

                    actionNodes.forEach(groupNode::addChild);
                    return groupNode;
                })
                .collect(Collectors.toList());

        List<RoleTreeNode> allNodes = new ArrayList<>();
        List<RoleTreeNode> finalAllNodes = allNodes;
        groupNodes.forEach(r -> {
            finalAllNodes.add(r);
            finalAllNodes.addAll(flatten(r.getChildren()));
        });

        if (nameFilterText != null && !nameFilterText.isEmpty()) {
            allNodes = allNodes.stream()
                    .filter(n -> n.getName() != null && n.getName().toLowerCase().contains(nameFilterText.toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (selectedCategory != null && !selectedCategory.isEmpty()) {
            allNodes = allNodes.stream()
                    .filter(n -> selectedCategory.equals(n.getCategory()))
                    .collect(Collectors.toList());
        }

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
                Set<RoleTreeNode> selected = new LinkedHashSet<>(entitiesTree.getSelectedItems());

                boolean groupsSelected = selected.stream().anyMatch(s -> "GROUP".equals(s.getNodeType()));
                if (groupsSelected) {
                    // Expand selected groups so their children are visible when user checks the group
                    selected.stream()
                            .filter(s -> "GROUP".equals(s.getNodeType()))
                            .forEach(entitiesTree::expand);

                    Set<RoleTreeNode> newSel = new LinkedHashSet<>();
                    for (RoleTreeNode s : selected) {
                        if ("GROUP".equals(s.getNodeType())) {
                            newSel.addAll(gatherPermissionDescendants(s));
                        } else {
                            newSel.add(s);
                        }
                    }

                    isAdjustingSelection = true;
                    entitiesTree.deselectAll();
                    newSel.forEach(entitiesTree::select);

                    handleSelectionChange(newSel);

                    entitiesTree.getDataProvider().refreshAll();
                    isAdjustingSelection = false;
                } else {
                    handleSelectionChange(selected);
                }
            });
            selectionListenerAttached = true;
        }

        entitiesTree.getDataProvider().refreshAll();
    }

    private void syncAssignments(Collection<RoleTreeNode> selected) {
        Collection<RoleTreeNode> all = entitiesDc.getItems();
        for (RoleTreeNode item : all) {
            if ("PERMISSION".equals(item.getNodeType())) {
                boolean sel = selected.contains(item);
                item.setAssigned(sel, false);
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

    private void applyInitialSelectionFromExistingAssignments() {
        if (existingAssignedRoleCodes == null || existingAssignedRoleCodes.isEmpty()) {
            syncAssignments(Collections.emptyList());
            entitiesTree.getDataProvider().refreshAll();
            return;
        }

        Set<RoleTreeNode> toSelect = new LinkedHashSet<>();
        for (String dbCode : existingAssignedRoleCodes) {
            if (dbCode == null) continue;
            String norm = dbCode.trim().toLowerCase();

            Optional<RoleTreeNode> found = entitiesDc.getItems().stream()
                    .filter(n -> "PERMISSION".equals(n.getNodeType()))
                    .filter(n -> {
                        String c = n.getCode() != null ? n.getCode().trim().toLowerCase() : null;
                        String r = n.getResource() != null ? n.getResource().trim().toLowerCase() : null;
                        return (c != null && c.equals(norm)) || (r != null && r.equals(norm));
                    })
                    .findFirst();

            found.ifPresent(toSelect::add);
        }

        if (toSelect.isEmpty()) {
            syncAssignments(Collections.emptyList());
            entitiesTree.getDataProvider().refreshAll();
            return;
        }

        isAdjustingSelection = true;
        try {
            entitiesTree.deselectAll();
            toSelect.forEach(entitiesTree::select);

            syncAssignments(toSelect);
            entitiesTree.getDataProvider().refreshAll();
        } finally {
            isAdjustingSelection = false;
        }
    }

    private void handleSelectionChange(Collection<RoleTreeNode> selectedNodes) {
        if (isAdjustingSelection) return;

        if (subjectUsername == null || subjectUsername.isEmpty()) {
            syncAssignments(selectedNodes);
            entitiesTree.getDataProvider().refreshAll();
            return;
        }

        Set<String> selectedCodes = selectedNodes.stream()
                .filter(n -> "PERMISSION".equals(n.getNodeType()))
                .map(n -> n.getCode() != null ? n.getCode() : n.getResource())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> toAdd = new HashSet<>(selectedCodes);
        toAdd.removeAll(existingAssignedRoleCodes);

        Set<String> toRemove = new HashSet<>(existingAssignedRoleCodes);
        toRemove.removeAll(selectedCodes);

        for (String code : toAdd) {
            try {
                RoleAssignmentEntity ent = metadata.create(RoleAssignmentEntity.class);
                ent.setUsername(subjectUsername);
                ent.setRoleCode(code);
                ent.setRoleType(RoleAssignmentRoleType.RESOURCE);
                dataManager.save(ent);
                existingAssignedRoleCodes.add(code);
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }

        if (!toRemove.isEmpty()) {
            try {
                List<RoleAssignmentEntity> found = dataManager.load(RoleAssignmentEntity.class)
                        .query("select r from sec_RoleAssignmentEntity r where r.username = :username and r.roleCode in :codes and r.roleType = :type")
                        .parameter("username", subjectUsername)
                        .parameter("codes", toRemove)
                        .parameter("type", RoleAssignmentRoleType.RESOURCE)
                        .list();
                if (!found.isEmpty()) {
                    dataManager.remove(found);
                    existingAssignedRoleCodes.removeAll(toRemove);
                }
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }

        syncAssignments(selectedNodes);
        entitiesTree.getDataProvider().refreshAll();
    }

    @Subscribe("nameFilter")
    public void onNameFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        nameFilterText = event.getValue() != null ? event.getValue() : "";
        loadRoles();
        setupColumnsAndAssigned();
        applyInitialSelectionFromExistingAssignments();
    }

    @Subscribe("categoryFilter")
    public void onCategoryFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        selectedCategory = event.getValue();
        loadRoles();
        setupColumnsAndAssigned();
        applyInitialSelectionFromExistingAssignments();
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
