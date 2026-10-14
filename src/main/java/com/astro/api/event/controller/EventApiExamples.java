package com.astro.api.event.controller;

/** Examples use fictional identities and persisted IDs, matching the API envelopes. */
public final class EventApiExamples {
    private EventApiExamples() {}

    public static final String DETAIL = """
            {
              "success": true,
              "message": "Evento retornado com sucesso",
              "data": {
                "id": 1,
                "title": "Treinamento NR 10",
                "description": "Orientações do treinamento",
                "nrId": 10,
                "externalLink": "https://example.com/treinamento",
                "completionMode": "GESTOR",
                "mandatoryEvidence": true,
                "status": "ATIVO",
                "cancellationDate": null,
                "cancellationReason": null,
                "managerId": 10,
                "canEdit": true,
                "canCancel": true,
                "detailsEditable": true,
                "eventClasses": [
                  {
                    "id": 2,
                    "name": "Grupo A",
                    "startDate": "2026-11-05T08:00:00",
                    "endDate": "2026-11-05T12:00:00",
                    "editable": true,
                    "participants": [
                      {
                        "id": 20,
                        "name": "Maria",
                        "email": "maria@example.com",
                        "participationId": 30
                      }
                    ]
                  }
                ]
              },
              "errors": null,
              "path": "/events/1"
            }
            """;

    public static final String CREATE = """
            {
              "success": true,
              "message": "Evento criado com sucesso",
              "data": {
                "id": 1,
                "title": "Treinamento NR 10",
                "description": "Orientações do treinamento",
                "nrId": 10,
                "externalLink": "https://example.com/treinamento",
                "completionMode": "GESTOR",
                "mandatoryEvidence": true,
                "status": "ATIVO",
                "cancellationDate": null,
                "cancellationReason": null,
                "managerId": 10,
                "canEdit": true,
                "canCancel": true,
                "detailsEditable": true,
                "eventClasses": [
                  {
                    "id": 2,
                    "name": "Grupo A",
                    "startDate": "2026-11-05T08:00:00",
                    "endDate": "2026-11-05T12:00:00",
                    "editable": true,
                    "participants": [
                      {
                        "id": 20,
                        "name": "Maria",
                        "email": "maria@example.com",
                        "participationId": 30
                      }
                    ]
                  }
                ]
              },
              "errors": null,
              "path": "/events"
            }
            """;

    public static final String UPDATE = """
            {
              "success": true,
              "message": "Evento atualizado com sucesso",
              "data": {
                "id": 1,
                "title": "Treinamento NR 10",
                "description": "Orientações do treinamento",
                "nrId": 10,
                "externalLink": "https://example.com/treinamento",
                "completionMode": "GESTOR",
                "mandatoryEvidence": true,
                "status": "ATIVO",
                "cancellationDate": null,
                "cancellationReason": null,
                "managerId": 10,
                "canEdit": true,
                "canCancel": true,
                "detailsEditable": true,
                "eventClasses": [
                  {
                    "id": 2,
                    "name": "Grupo A",
                    "startDate": "2026-11-05T08:00:00",
                    "endDate": "2026-11-05T12:00:00",
                    "editable": true,
                    "participants": [
                      {
                        "id": 20,
                        "name": "Maria",
                        "email": "maria@example.com",
                        "participationId": 30
                      }
                    ]
                  }
                ]
              },
              "errors": null,
              "path": "/events/1"
            }
            """;

    public static final String CALENDAR = """
            {
              "success": true,
              "message": "Turmas retornadas com sucesso",
              "data": [
                {
                  "eventId": 1,
                  "eventTitle": "Treinamento NR 10",
                  "eventClassId": 2,
                  "className": "Grupo A",
                  "startDate": "2026-11-05T08:00:00",
                  "endDate": "2026-11-05T12:00:00",
                  "status": "ATIVO"
                }
              ],
              "errors": null,
              "path": "/events"
            }
            """;

    public static final String CANDIDATES = """
            {
              "success": true,
              "message": "Colaboradores retornados com sucesso",
              "data": [
                {
                  "id": 20,
                  "name": "Maria",
                  "email": "maria@example.com",
                  "roleId": 4,
                  "roleName": "Eletricista",
                  "unitId": 3,
                  "unitName": "Sede",
                  "workModel": "HIBRIDO"
                }
              ],
              "errors": null,
              "path": "/events/eligible-candidates"
            }
            """;

    public static final String CANCEL = """
            {
              "success": true,
              "message": "Evento cancelado com sucesso",
              "data": {
                "id": 1,
                "title": "Treinamento NR 10",
                "description": "Orientações do treinamento",
                "nrId": 10,
                "externalLink": "https://example.com/treinamento",
                "completionMode": "GESTOR",
                "mandatoryEvidence": true,
                "status": "CANCELADO",
                "cancellationDate": "2026-10-14T12:00:00Z",
                "cancellationReason": "Treinamento reagendado",
                "managerId": 10,
                "canEdit": false,
                "canCancel": false,
                "detailsEditable": false,
                "eventClasses": [
                  {
                    "id": 2,
                    "name": "Grupo A",
                    "startDate": "2026-11-05T08:00:00",
                    "endDate": "2026-11-05T12:00:00",
                    "editable": false,
                    "participants": [
                      {
                        "id": 20,
                        "name": "Maria",
                        "email": "maria@example.com",
                        "participationId": 30
                      }
                    ]
                  }
                ]
              },
              "errors": null,
              "path": "/events/1/cancellation"
            }
            """;

    public static final String CREATE_REQUEST = """
            {
              "title": "Treinamento NR 10",
              "description": "Orientações do treinamento",
              "nrId": 10,
              "externalLink": "https://example.com/treinamento",
              "completionMode": "GESTOR",
              "mandatoryEvidence": true,
              "eventClasses": [
                {
                  "name": "Grupo A",
                  "startDate": "2026-11-05T08:00:00",
                  "endDate": "2026-11-05T12:00:00",
                  "participantIds": [
                    20
                  ]
                }
              ]
            }
            """;

    public static final String UPDATE_REQUEST = """
            {
              "title": "Treinamento NR 10",
              "description": "Orientações do treinamento",
              "externalLink": "https://example.com/treinamento",
              "eventClasses": [
                {
                  "name": "Grupo A",
                  "startDate": "2026-11-05T08:00:00",
                  "endDate": "2026-11-05T12:00:00",
                  "participantIds": [
                    20
                  ],
                  "id": 2
                }
              ]
            }
            """;

    public static final String CANCEL_REQUEST = """
            {
              "reason": "Treinamento reagendado"
            }
            """;
}
