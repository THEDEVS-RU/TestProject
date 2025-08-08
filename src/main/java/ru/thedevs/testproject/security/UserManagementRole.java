package ru.thedevs.testproject.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import ru.thedevs.testproject.entity.User;

@ResourceRole(name = "User management", code = UserManagementRole.CODE, scope = "API")
public interface UserManagementRole {

    String CODE = "user-management";

    @EntityAttributePolicy(entityClass = User.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.ALL)
    void user();
}