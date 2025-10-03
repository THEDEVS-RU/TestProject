package ru.thedevs.testproject.view.security;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AssignmentService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private Metadata metadata;

    /**
     * Загружает текущие назначения ролей (roleCode) для пользователя.
     */
    public Set<String> loadExistingAssignedRoleCodes(String username) {
        Set<String> existingAssignedRoleCodes = new HashSet<>();
        if (username == null) return existingAssignedRoleCodes;

        try {
            List<RoleAssignmentEntity> assignments = dataManager.load(RoleAssignmentEntity.class)
                    .query("select r from sec_RoleAssignmentEntity r where r.username = :username and r.roleType = :type")
                    .parameter("username", username)
                    .parameter("type", RoleAssignmentRoleType.RESOURCE)
                    .list();

            assignments.stream()
                    .map(RoleAssignmentEntity::getRoleCode)
                    .filter(Objects::nonNull)
                    .forEach(existingAssignedRoleCodes::add);
        } catch (Exception ignored) {
        }
        return existingAssignedRoleCodes;
    }

    /**
     * Сохраняет добавленные и удаляет удалённые назначения (persistPendingAssignments).
     * Модифицирует переданный existingAssignedRoleCodes (добавляет/удаляет).
     */
    public void persistPendingAssignments(String subjectUsername, Set<String> pendingSelectedRoleCodes, Set<String> existingAssignedRoleCodes) {
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
}
