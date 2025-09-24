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

    // CONSOLE DIAGNOSTICS: включить / выключить вывод в консоль
    private static final boolean DIAGNOSTICS_ENABLED = true;

    public void setSubjectUsername(String subjectUsername) {
        this.subjectUsername = subjectUsername;
    }

    public void setExistingAssignedRoleCodes(Collection<String> codes) {
        this.existingAssignedRoleCodes.clear();
        if (codes != null) this.existingAssignedRoleCodes.addAll(codes);
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        loadRoles();
        setupColumnsAndAssigned();

        // Load DB assignments
        loadExistingAssignmentsIfNeeded();

        // CONSOLE DIAGNOSTICS: печатаем краткую сводку в консоль
        if (DIAGNOSTICS_ENABLED) {
            System.out.println("=== DIAGNOSTICS: onBeforeShow ===");
            System.out.println("subjectUsername = " + subjectUsername);
            System.out.println("existingAssignedRoleCodes (from DB) size = " + existingAssignedRoleCodes.size());
            System.out.println("existingAssignedRoleCodes = " + existingAssignedRoleCodes);
        }

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

                                // enabled/assignable logic
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

        // filters
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

    /**
     * CONSOLE DIAGNOSTICS:
     * Загружает назначения из DB и печатает их в консоль в расширенном виде (для копипаста).
     */
    private void loadExistingAssignmentsIfNeeded() {
        if (subjectUsername == null || subjectUsername.isEmpty()) return;
        if (!existingAssignedRoleCodes.isEmpty()) return;

        try {
            List<RoleAssignmentEntity> assignments = dataManager.load(RoleAssignmentEntity.class)
                    .query("select r from sec_RoleAssignmentEntity r where r.username = :username and r.roleType = :type")
                    .parameter("username", subjectUsername)
                    .parameter("type", RoleAssignmentRoleType.RESOURCE)
                    .list();

            System.out.println("=== DIAGNOSTICS: loadExistingAssignmentsIfNeeded() results ===");
            System.out.println("DB assignment rows count = " + assignments.size());

            for (RoleAssignmentEntity a : assignments) {
                String code = a.getRoleCode();
                if (code != null) existingAssignedRoleCodes.add(code);

                // печатаем детали каждой строки (копировать в чат)
                System.out.println("DB_ROW -> id=" + a.getId()
                        + ", username=" + a.getUsername()
                        + ", roleCode='" + a.getRoleCode()
                        + "', roleType='" + a.getRoleType() + "'");
            }

            System.out.println("existingAssignedRoleCodes after load = " + existingAssignedRoleCodes);
        } catch (Exception ex) {
            System.out.println("ERROR in loadExistingAssignmentsIfNeeded(): " + ex.getClass().getName() + " - " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    /**
     * CONSOLE DIAGNOSTICS + улучшенная логика применения selection.
     * - печатаем список ролей, которые есть в UI (детально)
     * - печатаем список кодов из БД
     * - печатаем коды, отсутствующие в UI
     * - выполняем selection по найденным узлам
     */
    private void applyInitialSelectionFromExistingAssignments() {
        // Print UI roles list in detail
        if (DIAGNOSTICS_ENABLED) {
            System.out.println("=== DIAGNOSTICS: roles present in UI (entitiesDc) ===");
            int i = 0;
            for (RoleTreeNode n : entitiesDc.getItems()) {
                i++;
                System.out.println(String.format(Locale.ROOT,
                        "UI[%d] id=%s type=%s name='%s' code='%s' resource='%s' assigned=%s enabled=%s children=%d",
                        i,
                        n.getId(), n.getNodeType(), n.getName(), n.getCode(), n.getResource(),
                        n.getAssigned(), n.getEnabled(),
                        n.getChildren() != null ? n.getChildren().size() : 0));
            }
            System.out.println("=== end UI roles list ===");
        }

        if (existingAssignedRoleCodes == null || existingAssignedRoleCodes.isEmpty()) {
            syncAssignments(Collections.emptyList());
            entitiesTree.getDataProvider().refreshAll();
            if (DIAGNOSTICS_ENABLED) {
                System.out.println("DIAGNOSTICS: existingAssignedRoleCodes is empty -> no selection will be applied");
            }
            return;
        }

        // collect codes in tree (normalized)
        Set<String> codesInTree = entitiesDc.getItems().stream()
                .filter(n -> "PERMISSION".equals(n.getNodeType()))
                .map(n -> {
                    String c = n.getCode();
                    String r = n.getResource();
                    return c != null ? c.trim() : (r != null ? r.trim() : null);
                })
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> normalizedDbCodes = existingAssignedRoleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> missingInTree = new HashSet<>(normalizedDbCodes);
        missingInTree.removeAll(codesInTree);

        if (DIAGNOSTICS_ENABLED) {
            System.out.println("DIAGNOSTICS: normalizedDbCodes = " + normalizedDbCodes);
            System.out.println("DIAGNOSTICS: codesInTree = " + codesInTree);
            System.out.println("DIAGNOSTICS: missingInTree (in DB but not in UI) = " + missingInTree);
        }

        // find nodes to select by matching normalized code/resource
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

            if (found.isPresent()) {
                toSelect.add(found.get());
                if (DIAGNOSTICS_ENABLED) {
                    System.out.println("DIAGNOSTICS: matched DB code '" + dbCode + "' -> node id=" + found.get().getId() + " name=" + found.get().getName());
                }
            } else {
                if (DIAGNOSTICS_ENABLED) {
                    System.out.println("DIAGNOSTICS: DB code '" + dbCode + "' not matched to any UI node");
                }
            }
        }

        if (toSelect.isEmpty()) {
            if (DIAGNOSTICS_ENABLED) {
                System.out.println("DIAGNOSTICS: no UI nodes matched for selection");
            }
            syncAssignments(Collections.emptyList());
            entitiesTree.getDataProvider().refreshAll();
            return;
        }

        // perform selection
        isAdjustingSelection = true;
        try {
            entitiesTree.deselectAll();
            toSelect.forEach(entitiesTree::select);

            syncAssignments(toSelect);
            entitiesTree.getDataProvider().refreshAll();

            if (DIAGNOSTICS_ENABLED) {
                System.out.println("DIAGNOSTICS: applied selection -> selected nodes count = " + toSelect.size());
            }
        } finally {
            isAdjustingSelection = false;
        }
    }

    /**
     * CONSOLE DIAGNOSTICS:
     * handleSelectionChange печатает в консоль детальную информацию о добавлении/удалении назначений.
     */
    private void handleSelectionChange(Collection<RoleTreeNode> selectedNodes) {
        if (isAdjustingSelection) return;

        if (subjectUsername == null || subjectUsername.isEmpty()) {
            syncAssignments(selectedNodes);
            entitiesTree.getDataProvider().refreshAll();
            return;
        }

        // build selectedCodes set
        Set<String> selectedCodes = selectedNodes.stream()
                .filter(n -> "PERMISSION".equals(n.getNodeType()))
                .map(n -> n.getCode() != null ? n.getCode() : n.getResource())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> toAdd = new HashSet<>(selectedCodes);
        toAdd.removeAll(existingAssignedRoleCodes);

        Set<String> toRemove = new HashSet<>(existingAssignedRoleCodes);
        toRemove.removeAll(selectedCodes);

        if (DIAGNOSTICS_ENABLED) {
            System.out.println("=== DIAGNOSTICS: handleSelectionChange ===");
            System.out.println("selectedCodes = " + selectedCodes);
            System.out.println("existingAssignedRoleCodes (before) = " + existingAssignedRoleCodes);
            System.out.println("toAdd = " + toAdd);
            System.out.println("toRemove = " + toRemove);
        }

        // add
        for (String code : toAdd) {
            try {
                RoleAssignmentEntity ent = metadata.create(RoleAssignmentEntity.class);
                ent.setUsername(subjectUsername);
                ent.setRoleCode(code);
                ent.setRoleType(RoleAssignmentRoleType.RESOURCE);
                dataManager.save(ent);
                existingAssignedRoleCodes.add(code);
                if (DIAGNOSTICS_ENABLED) {
                    System.out.println("DIAGNOSTICS: added DB assignment for code = " + code);
                }
            } catch (Exception ex) {
                System.out.println("ERROR adding assignment for code " + code + ": " + ex.getClass().getName() + " - " + ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }

        // remove
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
                    if (DIAGNOSTICS_ENABLED) {
                        System.out.println("DIAGNOSTICS: removed DB assignments for codes = " + toRemove);
                    }
                } else {
                    if (DIAGNOSTICS_ENABLED) {
                        System.out.println("DIAGNOSTICS: no matching DB rows found to remove for codes = " + toRemove);
                    }
                }
            } catch (Exception ex) {
                System.out.println("ERROR removing assignments " + toRemove + ": " + ex.getClass().getName() + " - " + ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }

        // refresh UI flags
        syncAssignments(selectedNodes);
        entitiesTree.getDataProvider().refreshAll();

        if (DIAGNOSTICS_ENABLED) {
            System.out.println("existingAssignedRoleCodes (after) = " + existingAssignedRoleCodes);
            System.out.println("=== DIAGNOSTICS: handleSelectionChange END ===");
        }
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

    /**
     * CONSOLE DIAGNOSTICS helper:
     * Вручную подставляет код роли в existingAssignedRoleCodes и пробует применить selection.
     * Можно вызвать из отладчика (IDE) или добавить временную кнопку для вызова.
     */
    public void testManualSelection(String code) {
        System.out.println("DIAGNOSTICS: testManualSelection called with code = '" + code + "'");
        if (code == null || code.trim().isEmpty()) {
            System.out.println("DIAGNOSTICS: testManualSelection: пустой код");
            return;
        }
        existingAssignedRoleCodes.clear();
        existingAssignedRoleCodes.add(code.trim());
        applyInitialSelectionFromExistingAssignments();
    }
}
