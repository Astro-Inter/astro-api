package com.astro.api.user.service;

import com.astro.api.auth.service.FirebaseIdentityService;

record EmailChange(Long userId, String firebaseUid, FirebaseIdentityService.EmailState previous) { }
