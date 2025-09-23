package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
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

    @Autowired
    private ResourceRoleRepository roleRepository;

    private String nameFilterText = "";
    private String selectedCategory = null;

    private boolean isAdjustingSelection = false;
    private boolean selectionListenerAttached = false;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        loadRoles();
        setupColumnsAndAssigned(); // перестраиваем колонки и скрываем колонку выбора строк
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
                                actionNode.setParent(groupNode);
                                return actionNode;
                            })
                            .collect(Collectors.toList());

                    actionNodes.forEach(groupNode::addChild);
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

    private List<RoleTreeNode> flatten(List<RoleTreeNode> nodes) {
        List<RoleTreeNode> flat = new ArrayList<>();
        for (RoleTreeNode n : nodes) {
            flat.add(n);
            flat.addAll(flatten(n.getChildren()));
        }
        return flat;
    }

    /**
     * Перестроить колонки полностью и скрыть selection column (чекбоксы выбора строк),
     * оставить ровно одну колонку-компонент "assigned".
     */
    @SuppressWarnings("unchecked")
    private void setupColumnsAndAssigned() {
        // 1) удалить все текущие колонки — надёжно убирает любые автогенерированные колонки
        List<Grid.Column<RoleTreeNode>> current = new ArrayList<>(entitiesTree.getColumns());
        for (Grid.Column<RoleTreeNode> c : current) {
            entitiesTree.removeColumn(c);
        }

        // 2) добавить иерархическую колонку name (в ней появится треугольник разворачивания)
        Grid.Column<RoleTreeNode> nameCol;
        try {
            nameCol = entitiesTree.addHierarchyColumn(RoleTreeNode::getName)
                    .setHeader("Название")
                    .setKey("name")
                    .setFlexGrow(2);
        } catch (Throwable t) {
            // fallback если API не поддерживает addHierarchyColumn
            nameCol = entitiesTree.addColumn(RoleTreeNode::getName)
                    .setHeader("Название")
                    .setKey("name")
                    .setFlexGrow(2);
        }

        // 3) добавить единственную колонку с чекбоксом (component column)
        Grid.Column<RoleTreeNode> assignedCol = entitiesTree.addComponentColumn(node -> {
            Checkbox cb = new Checkbox(Boolean.TRUE.equals(node.getAssigned()));

            if ("GROUP".equals(node.getNodeType())) {
                boolean any = node.getChildren() != null && node.getChildren().stream().anyMatch(ch -> Boolean.TRUE.equals(ch.getAssigned()));
                boolean all = node.getChildren() != null && node.getChildren().stream().allMatch(ch -> Boolean.TRUE.equals(ch.getAssigned()));
                cb.setIndeterminate(any && !all);
                cb.setValue(all);
            } else {
                cb.setValue(Boolean.TRUE.equals(node.getAssigned()));
            }

            cb.addValueChangeListener((HasValue.ValueChangeEvent<Boolean> ev) -> {
                Boolean newValue = ev.getValue();
                if ("GROUP".equals(node.getNodeType())) {
                    setAssignedRecursivelyForPermissions(node, Boolean.TRUE.equals(newValue));
                } else {
                    node.setAssigned(Boolean.TRUE.equals(newValue));
                }

                updateParentsAssigned(node);
                syncSelectionAfterAssignedChange(node, Boolean.TRUE.equals(newValue));
                entitiesTree.getDataProvider().refreshAll();
            });

            return cb;
        }).setHeader("Назначена").setKey("assigned");

        // 4) добавить колонку category
        Grid.Column<RoleTreeNode> categoryCol = entitiesTree.addColumn(RoleTreeNode::getCategory)
                .setHeader("Категория")
                .setKey("category");

        // 5) установить порядок колонок: name, assigned, category
        entitiesTree.setColumnOrder(nameCol, assignedCol, categoryCol);

        // 6) скрыть колонку выбора строк (selection checkbox), если она присутствует
        hideSelectionColumnIfPresent();

        // 7) слушатель селекции (один раз)
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

                    syncAssignments(newSel);
                    entitiesTree.getDataProvider().refreshAll();
                    isAdjustingSelection = false;
                } else {
                    syncAssignments(selected);
                    entitiesTree.getDataProvider().refreshAll();
                }
            });
            selectionListenerAttached = true;
        }

        entitiesTree.getDataProvider().refreshAll();
    }

    /**
     * Пытаемся получить selection column через reflection (getSelectionColumn),
     * если не получилось — пытаемся найти колонку по ключу/названию и скрыть её.
     */
    private void hideSelectionColumnIfPresent() {
        try {
            Object selModel = entitiesTree.getSelectionModel();
            if (selModel != null) {
                Method getSelectionColumn = null;
                try {
                    getSelectionColumn = selModel.getClass().getMethod("getSelectionColumn");
                } catch (NoSuchMethodException ignored) { }

                if (getSelectionColumn != null) {
                    Object selColObj = getSelectionColumn.invoke(selModel);
                    if (selColObj != null) {
                        // try cast to Grid.Column
                        if (selColObj instanceof Grid.Column) {
                            Grid.Column<?> selCol = (Grid.Column<?>) selColObj;
                            selCol.setVisible(false);
                            return;
                        } else {
                            // try to call setVisible(false) reflectively
                            try {
                                Method setVisible = selColObj.getClass().getMethod("setVisible", boolean.class);
                                setVisible.invoke(selColObj, false);
                                return;
                            } catch (NoSuchMethodException | IllegalAccessException ex) {
                                // fall through to fallback
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
            // fall through to fallback
        }

        // Fallback: попытка найти колонку с ключом/текстом "selection" и скрыть её
        for (Grid.Column<RoleTreeNode> c : entitiesTree.getColumns()) {
            String key = null;
            try { key = c.getKey(); } catch (Throwable ignored) {}
            if (key != null && key.toLowerCase().contains("select")) {
                c.setVisible(false);
                return;
            }
            // иногда ключа нет — проверим header
            String header = null;
            try { header = c.getHeaderText(); } catch (Throwable ignored) {}
            if (header != null && header.toLowerCase().contains("select")) {
                c.setVisible(false);
                return;
            }
        }
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

    private void setAssignedRecursivelyForPermissions(RoleTreeNode node, boolean assigned) {
        if (node == null) return;
        if (node.getChildren() == null) return;
        for (RoleTreeNode c : node.getChildren()) {
            if ("PERMISSION".equals(c.getNodeType()) && c.getResource() != null) {
                c.setAssigned(assigned);
            }
            setAssignedRecursivelyForPermissions(c, assigned);
        }
    }

    private void updateParentsAssigned(RoleTreeNode node) {
        RoleTreeNode p = node.getParent();
        while (p != null) {
            boolean all = p.getChildren() != null && p.getChildren().stream().allMatch(c -> Boolean.TRUE.equals(c.getAssigned()));
            p.setAssigned(all, false);
            p = p.getParent();
        }
    }

    private void syncSelectionAfterAssignedChange(RoleTreeNode node, boolean assigned) {
        if (node == null) return;
        List<RoleTreeNode> perms = gatherPermissionDescendants(node);
        if (perms.isEmpty()) return;

        isAdjustingSelection = true;
        Set<RoleTreeNode> current = new LinkedHashSet<>(entitiesTree.getSelectedItems());

        if (assigned) {
            current.addAll(perms);
        } else {
            current.removeAll(perms);
        }

        entitiesTree.deselectAll();
        current.forEach(entitiesTree::select);

        syncAssignments(current);
        entitiesTree.getDataProvider().refreshAll();
        isAdjustingSelection = false;
    }

    @Subscribe("nameFilter")
    public void onNameFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        nameFilterText = event.getValue() != null ? event.getValue() : "";
        loadRoles();
        setupColumnsAndAssigned();
    }

    @Subscribe("categoryFilter")
    public void onCategoryFilterValueChange(HasValue.ValueChangeEvent<String> event) {
        selectedCategory = event.getValue();
        loadRoles();
        setupColumnsAndAssigned();
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
