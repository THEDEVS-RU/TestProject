package ru.thedevs.testproject.view.security;

import io.jmix.core.Messages;
import io.jmix.security.model.ResourceRole;
import io.jmix.security.role.ResourceRoleRepository;
import io.jmix.security.model.RoleSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.thedevs.testproject.dto.RoleTreeNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RoleLoaderService {

    @Autowired
    private ResourceRoleRepository roleRepository;

    @Autowired
    private Messages messages;

    /**
     * Возвращает список корневых групп (с children заполненными).
     */
    public List<RoleTreeNode> loadGroups() {
        Map<String, List<ResourceRole>> byPolicyGroup = roleRepository.getAllRoles().stream()
                .collect(Collectors.groupingBy(role -> {
                    if (role.getResourcePolicies() != null && !role.getResourcePolicies().isEmpty()) {
                        String policyGroup = role.getResourcePolicies().iterator().next().getPolicyGroup();
                        if (policyGroup == null) return RoleTreeNode.NO_GROUP;

                        String normalized = policyGroup
                                .replaceAll("(Edit|Read|Delete|Create)$", "")
                                .replaceAll("Role$", "");
                        if (normalized.isEmpty()) return RoleTreeNode.NO_GROUP;
                        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
                    }
                    return RoleTreeNode.NO_GROUP;
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

        return groupNodes;
    }

    public List<RoleTreeNode> buildFlatListFromGroups(List<RoleTreeNode> groups) {
        return RoleTreeNode.flatten(groups);
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
                    } catch (Throwable ignored) {
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
