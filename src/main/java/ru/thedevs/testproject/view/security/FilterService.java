package ru.thedevs.testproject.view.security;

import org.springframework.stereotype.Component;
import ru.thedevs.testproject.dto.RoleTreeNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class FilterService {

    /**
     * Фильтрует начальные группы по тексту и категории и возвращает flat-display список:
     * для каждой подходящей группы — сам узел + все его дочерние элементы (flatten).
     */
    public List<RoleTreeNode> applyFilter(List<RoleTreeNode> initialGroups, String text, String category) {
        if (initialGroups == null) return new ArrayList<>();

        String q = text != null ? text.trim() : "";
        String qLower = q.toLowerCase();

        List<RoleTreeNode> matchedGroups = new ArrayList<>();
        for (RoleTreeNode g : initialGroups) {
            boolean nameMatch = q.isEmpty() || (g.getName() != null && g.getName().toLowerCase().contains(qLower));
            boolean categoryMatch = (category == null || category.isEmpty() || category.equals(g.getCategory()));
            if (nameMatch && categoryMatch) matchedGroups.add(g);
        }

        List<RoleTreeNode> displayList = new ArrayList<>();
        for (RoleTreeNode g : matchedGroups) {
            displayList.add(g);
            List<RoleTreeNode> childrenFlat = RoleTreeNode.flatten(g.getChildren());
            if (!childrenFlat.isEmpty()) displayList.addAll(childrenFlat);
        }

        return displayList;
    }
}
