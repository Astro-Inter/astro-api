package com.astro.api.manager.service;

import com.astro.api.auth.service.FirebaseIdentityService;

record EmailChange(Long managerId, String firebaseUid, FirebaseIdentityService.EmailState previous) { }
