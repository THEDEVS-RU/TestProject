package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
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

    private final Set<String> pendingSelectedRoleCodes = new LinkedHashSet<>();

    private boolean isAdjustingSelection = false;
    private boolean selectionListenerAttached = false;

    private Set<RoleTreeNode> lastSelected = new LinkedHashSet<>();

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

        applyInitialSelectionFromPending();
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

    @Subscribe("selectAction")
    public void onSelectAction(ActionPerformedEvent event) {
        persistPendingAssignments();

        clearGroupVisualChecks();

        pendingSelectedRoleCodes.clear();
        pendingSelectedRoleCodes.addAll(existingAssignedRoleCodes);
        applyInitialSelectionFromPending();

        handleSelection();
    }

    private void persistPendingAssignments() {
        if (subjectUsername == null || subjectUsername.isEmpty()) {
            existingAssignedRoleCodes.clear();
            existingAssignedRoleCodes.addAll(pendingSelectedRoleCodes);
            return;
        }

        Set<String> toAdd = new HashSet<>(pendingSelectedRoleCodes);
        toAdd.removeAll(existingAssignedRoleCodes);

        Set<String> toRemove = new HashSet<>(existingAssignedRoleCodes);
        toRemove.removeAll(pendingSelectedRoleCodes);

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
    }

    private void clearGroupVisualChecks() {
        Collection<RoleTreeNode> all = entitiesDc.getItems();
        if (all == null) return;

        List<RoleTreeNode> groups = all.stream()
                .filter(n -> "GROUP".equals(n.getNodeType()))
                .collect(Collectors.toList());
        for (RoleTreeNode g : groups) {
            g.setVisualAssigned(false, false);
            try {
                entitiesTree.getDataProvider().refreshItem(g);
            } catch (Exception ignored) { }
        }
    }

    @Subscribe("nameFilter")
    public void onNameFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        nameFilterText = event.getValue() != null ? event.getValue() : "";
        loadRoles();
        setupColumnsAndAssigned();
        applyInitialSelectionFromPending();
    }

    @Subscribe("categoryFilter")
    public void onCategoryFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        selectedCategory = event.getValue();
        loadRoles();
        setupColumnsAndAssigned();
        applyInitialSelectionFromPending();
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
