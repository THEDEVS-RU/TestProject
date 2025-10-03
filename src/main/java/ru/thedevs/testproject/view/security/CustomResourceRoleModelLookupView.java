package ru.thedevs.testproject.view.security;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import io.jmix.flowui.view.navigation.RouteSupport;
import io.jmix.flowui.view.navigation.UrlParamSerializer;
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

    @ViewComponent
    private TextField nameFilter;

    @ViewComponent
    private ComboBox<String> categoryFilter;

    @Autowired
    private Messages messages;

    @Autowired
    private RouteSupport routeSupport;

    @Autowired
    protected UrlParamSerializer urlParamSerializer;

    @Autowired
    private RoleLoaderService roleLoaderService;

    @Autowired
    private FilterService filterService;

    @Autowired
    private SelectionService selectionService;

    @Autowired
    private AssignmentService assignmentService;

    // минимальное состояние в view — имя субъекта (user) и коллекции, которые используют UI
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

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        locUserName();

        // load roles (groups + flat nodes)
        List<RoleTreeNode> groups = roleLoaderService.loadGroups();
        List<RoleTreeNode> allFlat = roleLoaderService.buildFlatListFromGroups(groups);

        initialGroups.clear();
        initialGroups.addAll(groups);

        initialNodes.clear();
        initialNodes.addAll(allFlat);

        entitiesDc.setItems(new ArrayList<>(initialNodes));

        setupColumnsAndAssigned();

        // load assignments
        existingAssignedRoleCodes.clear();
        existingAssignedRoleCodes.addAll(assignmentService.loadExistingAssignedRoleCodes(subjectUsername));

        pendingSelectedRoleCodes.clear();
        pendingSelectedRoleCodes.addAll(existingAssignedRoleCodes);

        if (categoryFilter != null) {
            categoryFilter.setItems("Системные", "Пользовательские");
            categoryFilter.setClearButtonVisible(true);
        }

        attachFilterListenersIfNeeded();

        applyInitialSelectionFromPending();
    }

    private void locUserName() {
        try {
            Location loc = routeSupport.getActiveViewLocation(UI.getCurrent());
            String path = loc.getPath();
            if (path != null && !path.isEmpty()) {
                String[] segments = path.split("/");
                for (int i = 0; i < segments.length; i++) {
                    if ("roleassignment".equals(segments[i]) && i + 1 < segments.length) {
                        String encoded = segments[i + 1];
                        try {
                            subjectUsername = urlParamSerializer.deserialize(String.class, encoded);
                        } catch (Exception ex) {
                            subjectUsername = encoded;
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
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

    private void setupColumnsAndAssigned() {
        List<Grid.Column<RoleTreeNode>> current = new ArrayList<>(entitiesTree.getColumns());
        for (Grid.Column<RoleTreeNode> c : current) {
            entitiesTree.removeColumn(c);
        }

        Grid.Column<RoleTreeNode> nameCol;
        try {
            nameCol = entitiesTree.addHierarchyColumn(RoleTreeNode::getName)
                    .setHeader(messages.getMessage("ru.thedevs.testproject/view.roles.headerTitle"))
                    .setKey("name")
                    .setFlexGrow(2);
        } catch (Throwable t) {
            nameCol = entitiesTree.addColumn(RoleTreeNode::getName)
                    .setHeader(messages.getMessage("ru.thedevs.testproject/view.roles.headerTitle"))
                    .setKey("name")
                    .setFlexGrow(2);
        }

        Grid.Column<RoleTreeNode> categoryCol = entitiesTree.addColumn(RoleTreeNode::getCategory)
                .setHeader(messages.getMessage("ru.thedevs.testproject/view.roles.headerCategory"))
                .setKey("category");

        entitiesTree.setColumnOrder(nameCol, categoryCol);

        if (!selectionListenerAttached) {
            entitiesTree.addSelectionListener(event -> {
                if (isAdjustingSelection) return;

                Set<RoleTreeNode> currentSelected = new LinkedHashSet<>(entitiesTree.getSelectedItems());

                // compute desired selection using service
                Set<RoleTreeNode> desired = selectionService.computeDesiredSelection(currentSelected, lastSelected, entitiesTree);

                // get only permission nodes from desired
                Set<RoleTreeNode> desiredPerms = desired.stream()
                        .filter(n -> RoleTreeNode.PERMISSION.equals(n.getNodeType()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                // update pendingSelectedRoleCodes from desiredPerms
                Set<String> selectedCodes = selectionService.extractSelectedCodes(desiredPerms);
                pendingSelectedRoleCodes.clear();
                pendingSelectedRoleCodes.addAll(selectedCodes);

                isAdjustingSelection = true;
                try {
                    entitiesTree.deselectAll();
                    for (RoleTreeNode it : desired) {
                        entitiesTree.select(it);
                    }
                    // sync assigned flags for all items
                    selectionService.syncAssignmentsForAll(entitiesDc.getItems(), pendingSelectedRoleCodes);

                    // refresh permission nodes
                    selectionService.refreshNodes(entitiesTree, desiredPerms);
                } finally {
                    isAdjustingSelection = false;
                }

                lastSelected = new LinkedHashSet<>(entitiesTree.getSelectedItems());
            });
            selectionListenerAttached = true;
        }
    }

    private void applyInitialSelectionFromPending() {
        Collection<RoleTreeNode> all = entitiesDc.getItems();
        if (all == null) return;

        // sync assigned flags
        selectionService.syncAssignmentsForAll(all, pendingSelectedRoleCodes);

        // expand groups that have any selected child and set visualAssigned for groups
        for (RoleTreeNode node : all) {
            if (RoleTreeNode.GROUP.equals(node.getNodeType())) {
                List<RoleTreeNode> children = node.getChildren();
                if (children == null) continue;

                boolean anySelected = children.stream().anyMatch(c -> Boolean.TRUE.equals(c.getAssigned()));
                boolean allSelected = children.stream().allMatch(c -> Boolean.TRUE.equals(c.getAssigned()));

                if (anySelected) {
                    try {
                        entitiesTree.expand(node);
                    } catch (Exception ignored) {
                    }
                }

                node.setVisualAssigned(allSelected, false);
                node.setAssigned(allSelected, false);
            }
        }

        Set<RoleTreeNode> toSelect = all.stream()
                .filter(n -> RoleTreeNode.PERMISSION.equals(n.getNodeType()))
                .filter(n -> {
                    String code = n.getCode() != null ? n.getCode() : n.getResource();
                    return code != null && pendingSelectedRoleCodes.contains(code);
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        isAdjustingSelection = true;
        try {
            entitiesTree.deselectAll();
            toSelect.forEach(entitiesTree::select);

            selectionService.refreshNodes(entitiesTree, toSelect);
            lastSelected = new LinkedHashSet<>(entitiesTree.getSelectedItems());
        } finally {
            isAdjustingSelection = false;
        }
    }

    private void applyFilter(String text, String category) {
        if (initialGroups.isEmpty()) {
            List<RoleTreeNode> groups = roleLoaderService.loadGroups();
            List<RoleTreeNode> allFlat = roleLoaderService.buildFlatListFromGroups(groups);
            initialGroups.clear();
            initialGroups.addAll(groups);
            initialNodes.clear();
            initialNodes.addAll(allFlat);
        }

        List<RoleTreeNode> displayList = filterService.applyFilter(initialGroups, text, category);

        entitiesDc.setItems(displayList);
        setupColumnsAndAssigned();
        selectionService.syncAssignmentsForAll(entitiesDc.getItems(), pendingSelectedRoleCodes);
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

    @Subscribe("selectAction")
    public void onSelectAction(io.jmix.flowui.kit.action.ActionPerformedEvent event) {
        assignmentService.persistPendingAssignments(subjectUsername, pendingSelectedRoleCodes, existingAssignedRoleCodes);

        selectionService.clearGroupVisualChecks(entitiesDc.getItems(), entitiesTree);

        pendingSelectedRoleCodes.clear();
        pendingSelectedRoleCodes.addAll(existingAssignedRoleCodes);
        applyInitialSelectionFromPending();

        try {
            Method m = this.getClass().getMethod("handleSelection");
            if (m != null) {
                m.invoke(this);
            }
        } catch (NoSuchMethodException nsme) {
            // ignore
        } catch (Throwable ignored) {
        }
    }
}
