package com.astro.api.workspace.service;

import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.common.exception.SpreadsheetImportException;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.common.model.SpreadsheetImportMessage;
import com.astro.api.role.model.Role;
import com.astro.api.role.service.RoleService;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.service.UnitService;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.CollaboratorSpreadsheetReader;
import com.astro.api.user.service.CollaboratorSpreadsheetValidator;
import com.astro.api.user.service.UserBatchService;
import com.astro.api.workspace.dto.request.CreateWorkspaceRequest;
import com.astro.api.workspace.controller.WorkspaceController;
import com.astro.api.workspace.dto.request.WorkspaceRequest;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.mapper.WorkspaceManagerMapper;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.repository.WorkspaceRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkspaceRegistrationServiceTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FirebaseIdentityService firebase = mock(FirebaseIdentityService.class);
    private final UnitService units = mock(UnitService.class);
    private final RoleService roles = mock(RoleService.class);
    private final UserBatchService batches = mock(UserBatchService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Unit unit = new Unit();
    private final Role role = new Role();
    private final CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "test-password", "gestora@example.com", new WorkspaceRequest("Teste", "12345678000199"),
            List.of(new UnitRequest("Matriz", "01001000", "SP", "São Paulo", "Centro", "Rua Teste", "10", null)));
    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        Workspace workspace = new Workspace();
        workspace.id = 34L;
        unit.name = "Matriz";
        unit.workspace = workspace;
        role.setName("Dev");
        role.setActive(true);
        role.setWorkspace(workspace);
        when(workspaces.save(any())).thenReturn(workspace);
        when(units.createForWorkspace(any(), anyList())).thenReturn(List.of(unit));
        when(roles.createForWorkspace(any(), anyCollection())).thenReturn(Map.of("dev", role));
        when(firebase.createUser(anyString(), anyString(), anyString())).thenReturn("firebase-manager");
        CollaboratorMapper mapper = new CollaboratorMapper();
        SpreadsheetImportService spreadsheet = new SpreadsheetImportService(users, new CollaboratorSpreadsheetReader(),
                mapper, new CollaboratorSpreadsheetValidator(FACTORY.getValidator()));
        WorkspaceRegistrationTransactionalService transactional = new WorkspaceRegistrationTransactionalService(
                workspaces, users, units, spreadsheet, events, new WorkspaceMapper(), roles, batches, new WorkspaceManagerMapper(mapper));
        service = new WorkspaceService(workspaces, users, firebase, spreadsheet, transactional);
    }

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @ParameterizedTest
    @CsvSource({"xls, Presencial, PRESENCIAL", "xlsx, Presencial, PRESENCIAL",
            "xls, Remoto, REMOTO", "xlsx, Remoto, REMOTO", "xls, Híbrido, HIBRIDO", "xlsx, Híbrido, HIBRIDO"})
    void importsManagerPersonalDataFromBothExcelFormats(String format, String modality, WorkModel expected) throws Exception {
        var result = service.register(request, spreadsheet(format, "111.444.777-35", modality, false));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User manager = saved.getValue();
        assertEquals(34L, result.workspaceId());
        assertEquals("11144477735", manager.getCpf());
        assertEquals(expected, manager.getWorkModel());
        assertEquals("Gestora", manager.getName());
        assertEquals(request.email(), manager.getEmail());
        assertEquals(UserType.GESTOR_WORKSPACE, manager.getType());
        assertEquals(UserStatus.ATIVO, manager.getStatus());
        assertEquals("firebase-manager", manager.getFirebaseUid());
        assertSame(unit, manager.getUnit());
        assertSame(role, manager.getRole());
        assertNotNull(manager.getCreatedAt());
        verify(batches).saveAllPreRegistered(List.of());
    }

    @ParameterizedTest
    @CsvSource(value = {"'',''", "111.444.777-35,''", "'',Remoto"})
    void keepsManagerFieldsOptionalAndImportsThePresentField(String cpf, String modality) throws Exception {
        service.register(request, spreadsheet("xlsx", cpf, modality, false));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(cpf.isBlank() ? null : "11144477735", saved.getValue().getCpf());
        assertEquals(modality.isBlank() ? null : WorkModel.REMOTO, saved.getValue().getWorkModel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "abc", "111.111.111-11"})
    void rejectsInvalidManagerCpfBeforeCreatingAnyAccount(String cpf) throws Exception {
        MockMultipartFile file = spreadsheet("xlsx", cpf, "Remoto", false);
        SpreadsheetImportException error = assertThrows(SpreadsheetImportException.class,
                () -> service.register(request, file));

        assertEquals(List.of(new SpreadsheetImportMessage(2, "cpf", "CPF inválido")), error.getImportErrors());
        assertNoRegistration();
    }

    @Test
    void rejectsUnknownManagerModalityBeforeCreatingAnyAccount() throws Exception {
        MockMultipartFile file = spreadsheet("xlsx", "11144477735", "Desconhecida", false);
        SpreadsheetImportException error = assertThrows(SpreadsheetImportException.class,
                () -> service.register(request, file));

        assertEquals(List.of(new SpreadsheetImportMessage(2, "modalidade", "Modalidade inválida")), error.getImportErrors());
        assertNoRegistration();
    }

    @Test
    void rejectsManagerCpfAlreadyRegisteredEvenWithoutCollaborators() throws Exception {
        User existing = new User();
        existing.setCpf("11144477735");
        when(users.findByCpfIn(anyCollection())).thenReturn(List.of(existing));
        MockMultipartFile file = spreadsheet("xlsx", "111.444.777-35", "Remoto", false);
        SpreadsheetImportException error = assertThrows(SpreadsheetImportException.class,
                () -> service.register(request, file));

        assertEquals(List.of(new SpreadsheetImportMessage(2, "cpf", "CPF já cadastrado")), error.getImportErrors());
        assertNoRegistration();
    }

    @Test
    void rejectsCpfSharedByManagerAndCollaborator() throws Exception {
        MockMultipartFile file = spreadsheet("xlsx", "529.982.247-25", "Remoto", true);
        SpreadsheetImportException error = assertThrows(SpreadsheetImportException.class,
                () -> service.register(request, file));

        assertEquals(List.of(new SpreadsheetImportMessage(3, "cpf", "CPF duplicado na planilha")), error.getImportErrors());
        assertNoRegistration();
    }

    @Test
    void keepsCollaboratorsInTheirOwnBatchAndQueue() throws Exception {
        service.register(request, spreadsheet("xlsx", "11144477735", "Presencial", true));

        ArgumentCaptor<List<User>> saved = ArgumentCaptor.forClass(List.class);
        verify(batches).saveAllPreRegistered(saved.capture());
        User collaborator = saved.getValue().getFirst();
        assertEquals("52998224725", collaborator.getCpf());
        assertEquals(WorkModel.HIBRIDO, collaborator.getWorkModel());
        assertEquals(UserType.COLABORADOR, collaborator.getType());
        assertEquals(UserStatus.PRE_CADASTRADO, collaborator.getStatus());
        assertNull(collaborator.getFirebaseUid());
        ArgumentCaptor<PreRegisteredCollaboratorsCreatedEvent> event = ArgumentCaptor.forClass(PreRegisteredCollaboratorsCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(List.of("ana@example.com"), event.getValue().emails());
    }

    @Test
    void returnsManagerErrorsThroughTheExistingHttpEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(service, mock(PreRegistrationQueueService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        String data = """
                {"password":"test-password","email":"gestora@example.com",
                 "workspace":{"name":"Teste","cnpj":"12345678000199"},
                 "units":[{"name":"Matriz","cep":"01001000","state":"SP","city":"São Paulo",
                           "neighborhood":"Centro","street":"Rua Teste","number":"10"}]}
                """;
        MockMultipartFile json = new MockMultipartFile("data", "", "application/json", data.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/register-workspace")
                        .file(json)
                        .file(spreadsheet("xlsx", "123", "Desconhecida", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data[0].row").value(2))
                .andExpect(jsonPath("$.data[0].field").value("cpf"))
                .andExpect(jsonPath("$.data[0].message").value("CPF inválido"))
                .andExpect(jsonPath("$.data[1].field").value("modalidade"))
                .andExpect(jsonPath("$.errors[0]").value("Corrija as linhas indicadas e envie novamente"));
        assertNoRegistration();
    }

    @Test
    void compensatesFirebaseIdentityWhenManagerPersistenceFails() throws Exception {
        RuntimeException failure = new RuntimeException("Persistence failure");
        doThrow(failure).when(users).save(any());
        MockMultipartFile file = spreadsheet("xlsx", "11144477735", "Presencial", false);

        assertSame(failure, assertThrows(RuntimeException.class, () -> service.register(request, file)));
        verify(firebase).compensateCreatedUser("firebase-manager");
        verifyNoInteractions(batches, events);
    }

    private void assertNoRegistration() {
        verify(workspaces, never()).save(any());
        verify(users, never()).save(any());
        verifyNoInteractions(firebase, units, roles, batches, events);
    }

    private MockMultipartFile spreadsheet(String format, String cpf, String modality, boolean collaborator) throws Exception {
        try (Workbook workbook = format.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Cadastro");
            writeRow(sheet.createRow(0), List.of("Nome", "CPF", "Email", "Unidade", "Cargo", "Modalidade"));
            writeRow(sheet.createRow(1), List.of(" Gestora ", cpf, " GESTORA@example.com ", "Matriz", "Dev", modality));
            if (collaborator) {
                writeRow(sheet.createRow(2), List.of("Ana", "52998224725", "ana@example.com", "Matriz", "Dev", "Híbrido"));
            }
            workbook.write(bytes);
            return new MockMultipartFile("file", "cadastro." + format, "application/octet-stream", bytes.toByteArray());
        }
    }

    private void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }
}
