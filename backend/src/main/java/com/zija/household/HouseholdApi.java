package com.zija.household;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdApi {

    boolean isInitialized();

    Optional<HouseholdInfo> findHousehold();

    List<MemberInfo> findMembers(UUID householdId);

    Optional<MemberInfo> findMember(UUID householdId, UUID accountId);

    MemberInfo requireActiveMember(UUID accountId);

    boolean hasAtLeastRole(UUID accountId, MemberRole requiredRole);

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

    record HouseholdInfo(UUID id, String name, String timezone) {
    }

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
