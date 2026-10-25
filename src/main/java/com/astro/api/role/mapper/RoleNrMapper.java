package com.astro.api.role.mapper;

import com.astro.api.conformity.model.NrDocument;
import com.astro.api.role.dto.response.RoleNrOptionResponse;
import com.astro.api.role.dto.response.RoleNrResponse;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RoleNrMapper {
    public List<RoleNrOptionResponse> toOptions(List<NrDocument> documents,
            List<RoleNrResponse> linked, Set<Integer> persistedCodes) {
        Map<Integer, RoleNrOptionResponse> options = new HashMap<>();
        Set<Integer> linkedCodes = linked.stream().map(RoleNrResponse::nrCode).collect(Collectors.toSet());
        for (RoleNrResponse nr : linked) {
            if (persistedCodes.contains(nr.nrCode())) {
                options.put(nr.nrCode(), new RoleNrOptionResponse(nr.nrCode(), nr.description(), true));
            }
        }
        for (NrDocument nr : documents) {
            if (persistedCodes.contains(nr.getId())) {
                options.put(nr.getId(), new RoleNrOptionResponse(nr.getId(), nr.getDescription(),
                        linkedCodes.contains(nr.getId())));
            }
        }
        return options.values().stream().sorted(Comparator.comparing(RoleNrOptionResponse::nr)).toList();
    }
}
