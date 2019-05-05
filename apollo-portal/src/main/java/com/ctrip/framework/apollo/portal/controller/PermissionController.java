package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.RequestPrecondition;
import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.core.enums.EnvUtils;
import com.ctrip.framework.apollo.portal.constant.RoleType;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.vo.AppRolesAssignedUsers;
import com.ctrip.framework.apollo.portal.entity.vo.NamespaceEnvRolesAssignedUsers;
import com.ctrip.framework.apollo.portal.entity.vo.NamespaceRolesAssignedUsers;
import com.ctrip.framework.apollo.portal.entity.vo.PermissionCondition;
import com.ctrip.framework.apollo.portal.service.RoleInitializationService;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import com.google.common.collect.Sets;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;


@RestController
public class PermissionController {

  private final UserInfoHolder userInfoHolder;
  private final RolePermissionService rolePermissionService;
  private final UserService userService;
  private final RoleInitializationService roleInitializationService;

  public PermissionController(
      final UserInfoHolder userInfoHolder,
      final RolePermissionService rolePermissionService,
      final UserService userService,
      final RoleInitializationService roleInitializationService) {
    this.userInfoHolder = userInfoHolder;
    this.rolePermissionService = rolePermissionService;
    this.userService = userService;
    this.roleInitializationService = roleInitializationService;
  }

  @PostMapping("/apps/{appId}/initPermission")
  public ResponseEntity<Void> initAppPermission(@PathVariable String appId, @RequestBody String namespaceName) {
    roleInitializationService.initNamespaceEnvRoles(appId, namespaceName, userInfoHolder.getUser().getUserId());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/apps/{appId}/permissions/{permissionType}")
  public ResponseEntity<PermissionCondition> hasPermission(@PathVariable String appId, @PathVariable String permissionType) {
    PermissionCondition permissionCondition = new PermissionCondition();

    permissionCondition.setHasPermission(
        rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(), permissionType, appId));

    return ResponseEntity.ok().body(permissionCondition);
  }

  @GetMapping("/apps/{appId}/namespaces/{namespaceName}/permissions/{permissionType}")
  public ResponseEntity<PermissionCondition> hasPermission(@PathVariable String appId, @PathVariable String namespaceName,
                                                           @PathVariable String permissionType) {
    PermissionCondition permissionCondition = new PermissionCondition();

    permissionCondition.setHasPermission(
        rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(), permissionType,
            RoleUtils.buildNamespaceTargetId(appId, namespaceName)));

    return ResponseEntity.ok().body(permissionCondition);
  }

  @GetMapping("/apps/{appId}/envs/{env}/namespaces/{namespaceName}/permissions/{permissionType}")
  public ResponseEntity<PermissionCondition> hasPermission(@PathVariable String appId, @PathVariable String env, @PathVariable String namespaceName,
                                                           @PathVariable String permissionType) {
    PermissionCondition permissionCondition = new PermissionCondition();

    permissionCondition.setHasPermission(
        rolePermissionService.userHasPermission(userInfoHolder.getUser().getUserId(), permissionType,
            RoleUtils.buildNamespaceTargetId(appId, namespaceName, env)));

    return ResponseEntity.ok().body(permissionCondition);
  }

  @GetMapping("/permissions/root")
  public ResponseEntity<PermissionCondition> hasRootPermission() {
    PermissionCondition permissionCondition = new PermissionCondition();

    permissionCondition.setHasPermission(rolePermissionService.isSuperAdmin(userInfoHolder.getUser().getUserId()));

    return ResponseEntity.ok().body(permissionCondition);
  }


  @GetMapping("/apps/{appId}/envs/{env}/namespaces/{namespaceName}/role_users")
  public NamespaceEnvRolesAssignedUsers getNamespaceEnvRoles(@PathVariable String appId, @PathVariable String env, @PathVariable String namespaceName) {

    // validate env parameter
    if (Env.UNKNOWN == EnvUtils.transformEnv(env)) {
      throw new BadRequestException("env is illegal");
    }

    NamespaceEnvRolesAssignedUsers assignedUsers = new NamespaceEnvRolesAssignedUsers();
    assignedUsers.setNamespaceName(namespaceName);
    assignedUsers.setAppId(appId);
    assignedUsers.setEnv(Env.fromString(env));

    Set<UserInfo> releaseNamespaceUsers =
        rolePermissionService.queryUsersWithRole(RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName, env));
    assignedUsers.setReleaseRoleUsers(releaseNamespaceUsers);

    Set<UserInfo> modifyNamespaceUsers =
        rolePermissionService.queryUsersWithRole(RoleUtils.buildModifyNamespaceRoleName(appId, namespaceName, env));
    assignedUsers.setModifyRoleUsers(modifyNamespaceUsers);

    return assignedUsers;
  }

  @PreAuthorize(value = "@permissionValidator.hasAssignRolePermission(#appId)")
  @PostMapping("/apps/{appId}/envs/{env}/namespaces/{namespaceName}/roles/{roleType}")
  public ResponseEntity<Void> assignNamespaceEnvRoleToUser(@PathVariable String appId, @PathVariable String env, @PathVariable String namespaceName,
                                                           @PathVariable String roleType, @RequestBody String user) {
    checkUserExists(user);
    RequestPrecondition.checkArgumentsNotEmpty(user);

    if (!RoleType.isValidRoleType(roleType)) {
      throw new BadRequestException("role type is illegal");
    }

    // validate env parameter
    if (Env.UNKNOWN == EnvUtils.transformEnv(env)) {
      throw new BadRequestException("env is illegal");
    }
    Set<String> assignedUser = rolePermissionService.assignRoleToUsers(RoleUtils.buildNamespaceRoleName(appId, namespaceName, roleType, env),
        Sets.newHashSet(user), userInfoHolder.getUser().getUserId());
    if (CollectionUtils.isEmpty(assignedUser)) {
      throw new BadRequestException(user + "已授权");
    }

    return ResponseEntity.ok().build();
  }

  @PreAuthorize(value = "@permissionValidator.hasAssignRolePermission(#appId)")
  @DeleteMapping("/apps/{appId}/envs/{env}/namespaces/{namespaceName}/roles/{roleType}")
  public ResponseEntity<Void> removeNamespaceEnvRoleFromUser(@PathVariable String appId, @PathVariable String env, @PathVariable String namespaceName,
                                                             @PathVariable String roleType, @RequestParam String user) {
    RequestPrecondition.checkArgumentsNotEmpty(user);

    if (!RoleType.isValidRoleType(roleType)) {
      throw new BadRequestException("role type is illegal");
    }
    // validate env parameter
    if (Env.UNKNOWN == EnvUtils.transformEnv(env)) {
      throw new BadRequestException("env is illegal");
    }
    rolePermissionService.removeRoleFromUsers(RoleUtils.buildNamespaceRoleName(appId, namespaceName, roleType, env),
        Sets.newHashSet(user), userInfoHolder.getUser().getUserId());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/apps/{appId}/namespaces/{namespaceName}/role_users")
  public NamespaceRolesAssignedUsers getNamespaceRoles(@PathVariable String appId, @PathVariable String namespaceName) {

    NamespaceRolesAssignedUsers assignedUsers = new NamespaceRolesAssignedUsers();
    assignedUsers.setNamespaceName(namespaceName);
    assignedUsers.setAppId(appId);

    Set<UserInfo> releaseNamespaceUsers =
        rolePermissionService.queryUsersWithRole(RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName));
    assignedUsers.setReleaseRoleUsers(releaseNamespaceUsers);

    Set<UserInfo> modifyNamespaceUsers =
        rolePermissionService.queryUsersWithRole(RoleUtils.buildModifyNamespaceRoleName(appId, namespaceName));
    assignedUsers.setModifyRoleUsers(modifyNamespaceUsers);

    return assignedUsers;
  }

  @PreAuthorize(value = "@permissionValidator.hasAssignRolePermission(#appId)")
  @PostMapping("/apps/{appId}/namespaces/{namespaceName}/roles/{roleType}")
  public ResponseEntity<Void> assignNamespaceRoleToUser(@PathVariable String appId, @PathVariable String namespaceName,
                                                        @PathVariable String roleType, @RequestBody String user) {
    checkUserExists(user);
    RequestPrecondition.checkArgumentsNotEmpty(user);

    if (!RoleType.isValidRoleType(roleType)) {
      throw new BadRequestException("role type is illegal");
    }
    Set<String> assignedUser = rolePermissionService.assignRoleToUsers(RoleUtils.buildNamespaceRoleName(appId, namespaceName, roleType),
        Sets.newHashSet(user), userInfoHolder.getUser().getUserId());
    if (CollectionUtils.isEmpty(assignedUser)) {
      throw new BadRequestException(user + "已授权");
    }

    return ResponseEntity.ok().build();
  }

  @PreAuthorize(value = "@permissionValidator.hasAssignRolePermission(#appId)")
  @DeleteMapping("/apps/{appId}/namespaces/{namespaceName}/roles/{roleType}")
  public ResponseEntity<Void> removeNamespaceRoleFromUser(@PathVariable String appId, @PathVariable String namespaceName,
                                                          @PathVariable String roleType, @RequestParam String user) {
    RequestPrecondition.checkArgumentsNotEmpty(user);

    if (!RoleType.isValidRoleType(roleType)) {
      throw new BadRequestException("role type is illegal");
    }
    rolePermissionService.removeRoleFromUsers(RoleUtils.buildNamespaceRoleName(appId, namespaceName, roleType),
        Sets.newHashSet(user), userInfoHolder.getUser().getUserId());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/apps/{appId}/role_users")
  public AppRolesAssignedUsers getAppRoles(@PathVariable String appId) {
    AppRolesAssignedUsers users = new AppRolesAssignedUsers();
    users.setAppId(appId);

    Set<UserInfo> masterUsers = rolePermissionService.queryUsersWithRole(RoleUtils.buildAppMasterRoleName(appId));
    users.setMasterUsers(masterUsers);

    return users;
  }

  @PreAuthorize(value = "@permissionValidator.hasAssignRolePermission(#appId)")
  @PostMapping("/apps/{appId}/roles/{roleType}")
  public ResponseEntity<Void> assignAppRoleToUser(@PathVariable String appId, @PathVariable String roleType,
                                                  @RequestBody String user) {
    checkUserExists(user);
    RequestPrecondition.checkArgumentsNotEmpty(user);

    if (!RoleType.isValidRoleType(roleType)) {
      throw new BadRequestException("role type is illegal");
    }
    Set<String> assignedUsers = rolePermissionService.assignRoleToUsers(RoleUtils.buildAppRoleName(appId, roleType),
        Sets.newHashSet(user), userInfoHolder.getUser().getUserId());
    if (CollectionUtils.isEmpty(assignedUsers)) {
      throw new BadRequestException(user + "已授权");
    }

    return ResponseEntity.ok().build();
  }

  @PreAuthorize(value = "@permissionValidator.hasAssignRolePermission(#appId)")
  @DeleteMapping("/apps/{appId}/roles/{roleType}")
  public ResponseEntity<Void> removeAppRoleFromUser(@PathVariable String appId, @PathVariable String roleType,
                                                    @RequestParam String user) {
    RequestPrecondition.checkArgumentsNotEmpty(user);

    if (!RoleType.isValidRoleType(roleType)) {
      throw new BadRequestException("role type is illegal");
    }
    rolePermissionService.removeRoleFromUsers(RoleUtils.buildAppRoleName(appId, roleType),
        Sets.newHashSet(user), userInfoHolder.getUser().getUserId());
    return ResponseEntity.ok().build();
  }

  private void checkUserExists(String userId) {
    if (userService.findByUserId(userId) == null) {
      throw new BadRequestException(String.format("User %s does not exist!", userId));
    }
  }

}
