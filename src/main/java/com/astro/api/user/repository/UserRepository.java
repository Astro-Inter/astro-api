package com.astro.api.user.repository;

import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.workspace.model.Workspace;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    long countByUnit_IdAndType(Long unitId, UserType type);

    long countByUnit_WorkspaceAndType(Workspace workspace, UserType type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u set u.type = :newType
            where u.id = :userId and u.type = :expectedType and u.status = :expectedStatus
            """)
    int updateTypeIfCurrent(@Param("userId") Long userId,
                          @Param("expectedType") UserType expectedType,
                          @Param("newType") UserType newType,
                          @Param("expectedStatus") UserStatus expectedStatus);

    Optional<User> findByFirebaseUid(String firebaseUid);

    @Query("""
            select u.id as id, u.name as name, u.type as role, workspace.id as workspaceId
            from User u join u.unit unit join unit.workspace workspace
            where u.firebaseUid = :firebaseUid
            """)
    Optional<UserChatProjection> findChatUserByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    @Query("""
            select u.id as id, u.name as name, u.type as role, workspace.id as workspaceId
            from User u join u.unit unit join unit.workspace workspace
            where u.id in :ids
            """)
    List<UserChatProjection> findChatUsersByIds(@Param("ids") Collection<Long> ids);
    @Query("""
            select recipient.id as id, recipient.name as name, recipientUnit.name as unitName
            from User recipient
            join recipient.unit recipientUnit
            where recipientUnit.workspace.id = :workspaceId
              and recipient.id <> :currentManagerId
              and recipient.status = :status
              and recipientUnit.isActive = true
              and recipient.type in :managerTypes
            order by recipient.name asc, recipient.id asc
            """)
    List<FormRecipientProjection> findFormRecipients(@Param("workspaceId") Long workspaceId,
                                                     @Param("currentManagerId") Long currentManagerId,
                                                     @Param("status") UserStatus status,
                                                     @Param("managerTypes") Collection<UserType> managerTypes);

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByEmailInIgnoreCase(Collection<String> emails);

    List<User> findByCpfIn(Collection<String> cpfs);

    @Query(value = """
            SELECT imported_user.* FROM usuario imported_user
            WHERE imported_user.cpf IN (:cpfs) OR lower(imported_user.email) IN (:emails)
            ORDER BY imported_user.id_usuario
            FOR UPDATE OF imported_user
            """, nativeQuery = true)
    List<User> findImportIdentitiesForUpdate(@Param("cpfs") Collection<String> cpfs,
                                           @Param("emails") Collection<String> emails);

    List<User> findByUnit_WorkspaceAndStatus(Workspace workspace, UserStatus status);

    @EntityGraph(attributePaths = {"role", "role.workspace", "unit", "unit.workspace", "unit.address"})
    @Query("select u from User u where u.id in :ids and u.unit.workspace = :workspace")
    List<User> findEventParticipantsInWorkspace(@Param("ids") Collection<Long> ids,
                                               @Param("workspace") Workspace workspace);

    @Query(value = "SELECT fn_retornar_tipo_conta(:email)", nativeQuery = true)
    String findAccountTypeByEmail(@Param("email") String email);

    @Query(value = """
            SELECT cargo_nr.nr_id AS nrId,
                   (
                       SELECT conformidade.data_validade
                       FROM conformidade
                       WHERE conformidade.usuario_id = :userId
                         AND conformidade.nr_id = cargo_nr.nr_id
                       ORDER BY conformidade.id_conformidade DESC
                       LIMIT 1
                   ) AS validity
            FROM cargo_nr
            WHERE cargo_nr.cargo_id = :roleId
            ORDER BY cargo_nr.nr_id
            """, nativeQuery = true)
    List<UserNrValidityProjection> findNrValiditiesByRoleIdAndUserId(
            @Param("roleId") Long roleId,
            @Param("userId") Long userId);
}
