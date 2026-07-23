package com.zija.household;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 家庭模块公共 API，提供家庭初始化状态查询、成员管理及角色鉴权能力。
 */
public interface HouseholdApi {

    /** 判断系统是否已完成家庭初始化。 */
    boolean isInitialized();

    /** 查询当前家庭信息。 */
    Optional<HouseholdInfo> findHousehold();

    /** 查询指定家庭的全部成员列表。 */
    List<MemberInfo> findMembers(UUID householdId);

    /** 查询指定家庭中某个账户对应的成员信息。 */
    Optional<MemberInfo> findMember(UUID householdId, UUID accountId);

    /** 获取指定账户的活跃成员信息，非活跃状态则抛出异常。 */
    MemberInfo requireActiveMember(UUID accountId);

    /** 判断指定账户是否至少拥有给定角色等级。 */
    boolean hasAtLeastRole(UUID accountId, MemberRole requiredRole);

    /** 家庭成员角色，按权限等级从低到高排列。 */
    enum MemberRole {
        OWNER(3), ADMIN(2), MEMBER(1);

        private final int authorityLevel;

        MemberRole(int authorityLevel) {
            this.authorityLevel = authorityLevel;
        }

        public boolean isAtLeast(MemberRole other) {
            return authorityLevel >= other.authorityLevel;
        }
    }

    /** 家庭基本信息。 */
    record HouseholdInfo(UUID id, String name, String timezone) {
    }

    /** 家庭成员详细信息。 */
    record MemberInfo(
            UUID id,
            UUID householdId,
            UUID accountId,
            String username,
            String displayName,
            MemberRole role,
            String status
    ) {
    }
}
