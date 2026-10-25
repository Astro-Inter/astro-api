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
        Set<Integer> linkedCodes = linked.stream()
                .map(RoleNrResponse::nrCode)
                .collect(Collectors.toSet());

        for (RoleNrResponse nr : linked) {
            Integer nrCode = nr.nrCode();

            if (persistedCodes.contains(nrCode)) {
                RoleNrOptionResponse option = new RoleNrOptionResponse(
                        nrCode,
                        nr.description(),
                        true
                );

                options.put(nrCode, option);
            }
        }

        for (NrDocument nr : documents) {
            Integer nrCode = nr.getId();

            if (persistedCodes.contains(nrCode)) {
                boolean linkedToRole = linkedCodes.contains(nrCode);
                RoleNrOptionResponse option = new RoleNrOptionResponse(
                        nrCode,
                        nr.getDescription(),
                        linkedToRole
                );

                options.put(nrCode, option);
            }
        }

        List<RoleNrOptionResponse> sortedOptions = options.values()
                .stream()
                .sorted(Comparator.comparing(RoleNrOptionResponse::nr))
                .toList();

        return sortedOptions;
    }
}
