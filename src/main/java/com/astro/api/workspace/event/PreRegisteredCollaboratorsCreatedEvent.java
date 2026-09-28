package com.astro.api.workspace.event;

import java.util.List;

public record PreRegisteredCollaboratorsCreatedEvent(List<String> emails) { }
