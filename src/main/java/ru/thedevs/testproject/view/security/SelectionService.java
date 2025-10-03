package ru.thedevs.testproject.view.security;

import io.jmix.flowui.component.grid.TreeDataGrid;
import org.springframework.stereotype.Component;
import ru.thedevs.testproject.dto.RoleTreeNode;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SelectionService {

    /**
     * Вычисляет желаемый набор элементов для выделения, принимая текущий выбор и предыдущий.
     * Логика аналогична оригинальному обработчику selectionListener:
     * - если добавлена группа — добавить всех permission-потомков,
     * - если удалена группа — убрать всех permission-потомков,
     * - если удалён permission — снять его визуальную галочку.
     */
    public Set<RoleTreeNode> computeDesiredSelection(Set<RoleTreeNode> currentSelected, Set<RoleTreeNode> lastSelected, TreeDataGrid<RoleTreeNode> entitiesTree) {
        Set<RoleTreeNode> added = new LinkedHashSet<>(currentSelected);
        added.removeAll(lastSelected);

        Set<RoleTreeNode> removed = new LinkedHashSet<>(lastSelected);
        removed.removeAll(currentSelected);

        Set<RoleTreeNode> desired = new LinkedHashSet<>(currentSelected);

        for (RoleTreeNode a : added) {
            if (RoleTreeNode.GROUP.equals(a.getNodeType())) {
                a.setVisualAssigned(true, false);
                try {
                    entitiesTree.expand(a);
                } catch (Exception ignored) {}
                List<RoleTreeNode> perms = gatherPermissionDescendants(a);
                desired.addAll(perms);
                for (RoleTreeNode p : perms) {
                    p.setVisualAssigned(true, false);
                }
            }
        }

        for (RoleTreeNode r : removed) {
            if (RoleTreeNode.GROUP.equals(r.getNodeType())) {
                r.setVisualAssigned(false, false);
                List<RoleTreeNode> perms = gatherPermissionDescendants(r);
                desired.removeAll(perms);
                for (RoleTreeNode p : perms) {
                    p.setVisualAssigned(false, false);
                }
            } else if (RoleTreeNode.PERMISSION.equals(r.getNodeType())) {
                r.setVisualAssigned(false, false);
            }
        }

        return desired;
    }

    public List<RoleTreeNode> gatherPermissionDescendants(RoleTreeNode node) {
        List<RoleTreeNode> result = new ArrayList<>();
        if (node == null) return result;
        if (RoleTreeNode.PERMISSION.equals(node.getNodeType())) {
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
     * Из выбранных permission-узлов собирает набор кодов ролей (code или resource).
     */
    public Set<String> extractSelectedCodes(Collection<RoleTreeNode> selectedPermissions) {
        if (selectedPermissions == null) return new HashSet<>();
        return selectedPermissions.stream()
                .filter(n -> RoleTreeNode.PERMISSION.equals(n.getNodeType()))
                .map(n -> n.getCode() != null ? n.getCode() : n.getResource())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Синхронизирует assigned/visualAssigned у всех элементов (group & permission) на основании pendingSelectedRoleCodes.
     */
    public void syncAssignmentsForAll(Collection<RoleTreeNode> all, Set<String> pendingSelectedRoleCodes) {
        if (all == null) return;

        for (RoleTreeNode item : all) {
            if (RoleTreeNode.PERMISSION.equals(item.getNodeType())) {
                String code = item.getCode() != null ? item.getCode() : item.getResource();
                boolean sel = code != null && pendingSelectedRoleCodes.contains(code);
                item.setAssigned(sel, false);
                item.setVisualAssigned(sel, false);
            }
        }

        for (RoleTreeNode item : all) {
            if (RoleTreeNode.GROUP.equals(item.getNodeType())) {
                boolean allAssigned = item.getChildren() != null &&
                        item.getChildren().stream().allMatch(c -> Boolean.TRUE.equals(c.getAssigned()));
                item.setAssigned(allAssigned, false);
            }
        }
    }

    /**
     * Обновляет отображение конкретных нод в TreeDataGrid (refreshItem).
     */
    public void refreshNodes(TreeDataGrid<RoleTreeNode> entitiesTree, Collection<RoleTreeNode> nodes) {
        if (nodes == null) return;
        for (RoleTreeNode n : nodes) {
            try {
                entitiesTree.getDataProvider().refreshItem(n);
                if (n.getParent() != null) {
                    entitiesTree.getDataProvider().refreshItem(n.getParent());
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Сброс визуальных чеков у групп.
     */
    public void clearGroupVisualChecks(Collection<RoleTreeNode> all, TreeDataGrid<RoleTreeNode> entitiesTree) {
        if (all == null) return;
        List<RoleTreeNode> groups = all.stream()
                .filter(n -> RoleTreeNode.GROUP.equals(n.getNodeType()))
                .collect(Collectors.toList());
        for (RoleTreeNode g : groups) {
            g.setVisualAssigned(false, false);
            try {
                entitiesTree.getDataProvider().refreshItem(g);
            } catch (Exception ignored) {
            }
        }
    }
}
