package com.astro.api.user.repository;

import com.astro.api.user.model.UserProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfilePhotoRepository extends JpaRepository<UserProfilePhoto, Long> {
}
